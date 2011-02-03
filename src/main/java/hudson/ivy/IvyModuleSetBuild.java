/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Victor Glushenkov, Timothy Bingaman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.ivy;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Util;
import hudson.ivy.IvyBuild.ProxyImpl2;
import hudson.ivy.builder.IvyBuilderType;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.model.Environment;
import hudson.model.Fingerprint;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Cause.UpstreamCause;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.ivy.Ivy;
import org.apache.ivy.Ivy.IvyCallback;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.sort.SortOptions;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.util.Message;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link Build} for {@link IvyModuleSet}.
 *
 * <p>
 * A "build" of {@link IvyModuleSet} consists of:
 *
 * <ol>
 * <li>Update the workspace.
 * <li>Parse ivy.xml files
 * <li>Trigger module builds.
 * </ol>
 *
 * This object remembers the changelog and what {@link IvyBuild}s are done on
 * this.
 *
 * @author Timothy Bingaman
 */
public class IvyModuleSetBuild extends AbstractIvyBuild<IvyModuleSet, IvyModuleSetBuild> {
    /**
     * {@link IvyReporter}s that will contribute project actions. Can be null if
     * there's none.
     */
    /* package */List<IvyReporter> projectActionReporters;

    public IvyModuleSetBuild(IvyModuleSet job) throws IOException {
        super(job);
    }

    public IvyModuleSetBuild(IvyModuleSet project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    /**
     * Exposes {@code ANT_OPTS} to forked processes.
     *
     * When we fork Ant, we do so directly by executing Java, thus this
     * environment variable is pointless (we have to tweak JVM launch option
     * correctly instead, which can be seen in {@link IvyProcessFactory}), but
     * setting the environment variable explicitly is still useful in case this
     * Ant forks other Ant processes via normal way. See HUDSON-3644.
     */
    @Override
    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        EnvVars envs = super.getEnvironment(log);
        envs.putAll(project.getIvyBuilderType().getEnvironment());
        return envs;
    }

    /**
     * Displays the combined status of all modules.
     * <p>
     * More precisely, this picks up the status of this build itself, plus all
     * the latest builds of the modules that belongs to this build.
     */
    @Override
    public Result getResult() {
        Result r = super.getResult();

        for (IvyBuild b : getModuleLastBuilds().values()) {
            Result br = b.getResult();
            if (r == null)
                r = br;
            else if (br == Result.NOT_BUILT)
                continue; // UGLY: when computing combined status, ignore the
            // modules that were not built
            else if (br != null)
                r = r.combine(br);
        }

        return r;
    }

    /**
     * Returns the filtered changeset entries that match the given module.
     */
    /* package */List<ChangeLogSet.Entry> getChangeSetFor(final IvyModule mod) {
        return new ArrayList<ChangeLogSet.Entry>() {
            {
                for (ChangeLogSet.Entry e : getChangeSet()) {
                    if (isDescendantOf(e, mod)) {
                        add(e);
                    }
                }
            }

            /**
             * Does this change happen somewhere in the given module or its
             * descendants?
             */
            private boolean isDescendantOf(ChangeLogSet.Entry e, IvyModule mod) {
                for (String path : e.getAffectedPaths())
                    if (path.startsWith(mod.getRelativePathToModuleRoot()))
                        return true;
                return false;
            }
        };
    }

    /**
     * Computes the module builds that correspond to this build.
     * <p>
     * A module may be built multiple times (by the user action), so the value
     * is a list.
     */
    public Map<IvyModule, List<IvyBuild>> getModuleBuilds() {
        Collection<IvyModule> mods = getParent().getModules();

        // identify the build number range. [start,end)
        IvyModuleSetBuild nb = getNextBuild();
        int end = nb != null ? nb.getNumber() : Integer.MAX_VALUE;

        // preserve the order by using LinkedHashMap
        Map<IvyModule, List<IvyBuild>> r = new LinkedHashMap<IvyModule, List<IvyBuild>>(mods.size());

        for (IvyModule m : mods) {
            List<IvyBuild> builds = new ArrayList<IvyBuild>();
            IvyBuild b = m.getNearestBuild(number);
            while (b != null && b.getNumber() < end) {
                builds.add(b);
                b = b.getNextBuild();
            }
            r.put(m, builds);
        }

        return r;
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        // map corresponding module build under this object
        if (token.indexOf('$') > 0) {
            IvyModule m = getProject().getModule(token);
            if (m != null)
                return m.getBuildByNumber(getNumber());
        }
        return super.getDynamic(token, req, rsp);
    }

    /**
     * Computes the latest module builds that correspond to this build.
     */
    public Map<IvyModule, IvyBuild> getModuleLastBuilds() {
        Collection<IvyModule> mods = getParent().getModules();

        // identify the build number range. [start,end)
        IvyModuleSetBuild nb = getNextBuild();
        int end = nb != null ? nb.getNumber() : Integer.MAX_VALUE;

        // preserve the order by using LinkedHashMap
        Map<IvyModule, IvyBuild> r = new LinkedHashMap<IvyModule, IvyBuild>(mods.size());

        for (IvyModule m : mods) {
            IvyBuild b = m.getNearestOldBuild(end - 1);
            if (b != null && b.getNumber() >= getNumber())
                r.put(m, b);
        }

        return r;
    }

    public void registerAsProjectAction(IvyReporter reporter) {
        if (projectActionReporters == null)
            projectActionReporters = new ArrayList<IvyReporter>();
        projectActionReporters.add(reporter);
    }

    /**
     * Finds {@link Action}s from all the module builds that belong to this
     * {@link IvyModuleSetBuild}. One action per one {@link IvyModule}, and
     * newer ones take precedence over older ones.
     */
    public <T extends Action> List<T> findModuleBuildActions(Class<T> action) {
        Collection<IvyModule> mods = getParent().getModules();
        List<T> r = new ArrayList<T>(mods.size());

        // identify the build number range. [start,end)
        IvyModuleSetBuild nb = getNextBuild();
        int end = nb != null ? nb.getNumber() - 1 : Integer.MAX_VALUE;

        for (IvyModule m : mods) {
            IvyBuild b = m.getNearestOldBuild(end);
            while (b != null && b.getNumber() >= number) {
                T a = b.getAction(action);
                if (a != null) {
                    r.add(a);
                    break;
                }
                b = b.getPreviousBuild();
            }
        }

        return r;
    }

    @Override
    public void run() {
        run(new RunnerImpl());
        getProject().updateTransientActions();
    }

    @Override
    public Fingerprint.RangeSet getDownstreamRelationship(AbstractProject that) {
        Fingerprint.RangeSet rs = super.getDownstreamRelationship(that);
        for (List<IvyBuild> builds : getModuleBuilds().values())
            for (IvyBuild b : builds)
                rs.add(b.getDownstreamRelationship(that));
        return rs;
    }

    /**
     * Called when a module build that corresponds to this module set build has
     * completed.
     */
    /* package */void notifyModuleBuild(IvyBuild newBuild) {
        try {
            // update module set build number
            getParent().updateNextBuildNumber();

            // update actions
            Map<IvyModule, List<IvyBuild>> moduleBuilds = getModuleBuilds();

            // actions need to be replaced atomically especially
            // given that two builds might complete simultaneously.
            synchronized (this) {
                boolean modified = false;

                List<Action> actions = getActions();
                Set<Class<? extends AggregatableAction>> individuals = new HashSet<Class<? extends AggregatableAction>>();
                for (Action a : actions) {
                    if (a instanceof IvyAggregatedReport) {
                        IvyAggregatedReport mar = (IvyAggregatedReport) a;
                        mar.update(moduleBuilds, newBuild);
                        individuals.add(mar.getIndividualActionType());
                        modified = true;
                    }
                }

                // see if the new build has any new aggregatable action that we
                // haven't seen.
                for (AggregatableAction aa : newBuild.getActions(AggregatableAction.class)) {
                    if (individuals.add(aa.getClass())) {
                        // new AggregatableAction
                        IvyAggregatedReport mar = aa.createAggregatedAction(this, moduleBuilds);
                        mar.update(moduleBuilds, newBuild);
                        actions.add(mar);
                        modified = true;
                    }
                }

                if (modified) {
                    save();
                    getProject().updateTransientActions();
                }
            }

            // symlink to this module build
            String moduleFsName = newBuild.getProject().getModuleName().toFileSystemName();
            Util.createSymlink(getRootDir(),
                    "../../modules/" + moduleFsName + "/builds/" + newBuild.getId() /*ugly!*/,
                    moduleFsName, StreamTaskListener.NULL);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update " + this, e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to update " + this, e);
        }
    }

    /**
     * The sole job of the {@link IvyModuleSet} build is to update SCM and
     * triggers module builds.
     */
    private class RunnerImpl extends AbstractRunner {
        private Map<ModuleName,IvyBuild.ProxyImpl2> proxies;

        @Override
        protected Result doRun(final BuildListener listener) throws Exception {
            PrintStream logger = listener.getLogger();
            try {
                EnvVars envVars = getEnvironment(listener);

                parseIvyDescriptorFiles(listener, logger, envVars);

                if (!project.isAggregatorStyleBuild()) {
                    // start module builds
                    DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
                    Set<IvyModule> triggeredModules = new HashSet<IvyModule>();
                    if (!project.isIncrementalBuild() || IvyModuleSetBuild.this.getChangeSet().isEmptySet()) {
                        for (IvyModule module : project.sortedActiveModules) {
                            // Don't trigger builds if we've already triggered
                            // one
                            // of their dependencies.
                            // It's safe to just get the direct dependencies
                            // since
                            // the modules are sorted in dependency order.
                            List<AbstractProject> ups = module.getUpstreamProjects();
                            boolean triggerBuild = true;
                            for (AbstractProject upstreamDep : ups) {
                                if (triggeredModules.contains(upstreamDep)) {
                                    triggerBuild = false;
                                    break;
                                }
                            }

                            if (triggerBuild) {
                                logger.println("Triggering " + module.getModuleName());
                                module.scheduleBuild(new ParameterizedUpstreamCause(((Run<?, ?>) IvyModuleSetBuild.this), IvyModuleSetBuild.this.getActions(ParametersAction.class)));
                            }
                            triggeredModules.add(module);
                        }
                    } else {
                        for (IvyModule module : project.sortedActiveModules) {
                            // If there are changes for this module, add it.
                            // Also add it if we've never seen this module
                            // before,
                            // or if the previous build of this module
                            // failed or was unstable.
                            boolean triggerBuild = false;
                            if ((module.getLastBuild() == null) || (!getChangeSetFor(module).isEmpty())
                                    || (module.getLastBuild().getResult().isWorseThan(Result.SUCCESS))) {
                                triggerBuild = true;
                                List<AbstractProject> ups = module.getUpstreamProjects();
                                for (AbstractProject upstreamDep : ups) {
                                    if (triggeredModules.contains(upstreamDep)) {
                                        triggerBuild = false;
                                        triggeredModules.add(module);
                                        break;
                                    }
                                }
                            }

                            if (triggerBuild) {
                                logger.println("Triggering " + module.getModuleName());
                                module.scheduleBuild(new ParameterizedUpstreamCause(((Run<?, ?>) IvyModuleSetBuild.this), IvyModuleSetBuild.this.getActions(ParametersAction.class)));
                                triggeredModules.add(module);
                            }
                        }
                    }
                } else {
                    // do builds here
                    try {
                        List<BuildWrapper> wrappers = new ArrayList<BuildWrapper>();
                        for (BuildWrapper w : project.getBuildWrappersList())
                            wrappers.add(w);
                        ParametersAction parameters = getAction(ParametersAction.class);
                        if (parameters != null)
                            parameters.createBuildWrappers(IvyModuleSetBuild.this, wrappers);

                        for (BuildWrapper w : wrappers) {
                            Environment e = w.setUp(IvyModuleSetBuild.this, launcher, listener);
                            if (e == null)
                                return Result.FAILURE;
                            buildEnvironments.add(e);
                            e.buildEnvVars(envVars); // #3502: too late for
                            // getEnvironment to do
                            // this
                        }

                        if (!preBuild(listener, project.getPublishers()))
                            return Result.FAILURE;

                        List<String> changedModules = new ArrayList<String>();
                        for (IvyModule m : project.sortedActiveModules) {
                            // Check if incrementalBuild is selected and that
                            // there are changes -
                            // we act as if incrementalBuild is not set if there
                            // are no changes.
                            if (!IvyModuleSetBuild.this.getChangeSet().isEmptySet() && project.isIncrementalBuild()) {
                                // If there are changes for this module, add it.
                                if (!getChangeSetFor(m).isEmpty()) {
                                    changedModules.add(m.getModuleName().name);
                                }
                            }
                        }

                        Properties additionalProperties = null;
                        if (project.isAggregatorStyleBuild() && project.isIncrementalBuild()) {
                            additionalProperties = new Properties();
                            additionalProperties.put(project.getChangedModulesProperty() == null ? "hudson.ivy.changedModules" : project
                                    .getChangedModulesProperty(), StringUtils.join(changedModules, ','));
                        }     
                        
                        IvyBuilderType ivyBuilderType = project.getIvyBuilderType();
                        hudson.tasks.Builder builder = ivyBuilderType.getBuilder(additionalProperties, null, buildEnvironments);
                        logger.println("Building project with " + ivyBuilderType.getDescriptor().getDisplayName());
                        
                        if (builder.perform(IvyModuleSetBuild.this, launcher, listener))
                            return Result.SUCCESS;

                        return Result.FAILURE;
                    } finally {
                        // tear down in reverse order
                        boolean failed = false;
                        for (int i = buildEnvironments.size() - 1; i >= 0; i--) {
                            if (!buildEnvironments.get(i).tearDown(IvyModuleSetBuild.this, listener)) {
                                failed = true;
                            }
                        }
                        buildEnvironments = null;
                        // WARNING The return in the finally clause will trump
                        // any return before
                        if (failed)
                            return Result.FAILURE;
                    }
                }

                return null;
            } catch (AbortException e) {
                if (e.getMessage() != null)
                    listener.error(e.getMessage());
                return Result.FAILURE;
            } catch (InterruptedIOException e) {
                e.printStackTrace(listener.error("Aborted Ivy execution for InterruptedIOException"));
                return Result.ABORTED;
            } catch (InterruptedException e) {
                e.printStackTrace(listener.error("Aborted Ivy execution for InterruptedException"));
                return Result.ABORTED;
            } catch (IOException e) {
                e.printStackTrace(listener.error(Messages.IvyModuleSetBuild_FailedToParseIvyXml()));
                return Result.FAILURE;
            } catch (RunnerAbortedException e) {
                return Result.FAILURE;
            } catch (RuntimeException e) {
                // bug in the code.
                e.printStackTrace(listener.error("Processing failed due to a bug in the code. Please report this to users@hudson.dev.java.net"));
                logger.println("project=" + project);
                logger.println("project.getModules()=" + project.getModules());
                throw e;
            }
        }

        private void parseIvyDescriptorFiles(BuildListener listener, PrintStream logger, EnvVars envVars) throws IOException, InterruptedException {
            logger.println("Parsing Ivy Descriptor Files");

            List<IvyModuleInfo> ivyDescriptors;
            try {
            	IvyXmlParser parser = new IvyXmlParser(listener, project, getModuleRoot().getRemote());
            	if (getModuleRoot().getChannel() instanceof Channel)
            		((Channel) getModuleRoot().getChannel()).preloadJar(parser, Ivy.class);
                ivyDescriptors = getModuleRoot().act(parser);
            } catch (IOException e) {
                if (e.getCause() instanceof AbortException)
                    throw (AbortException) e.getCause();
                throw e;
            } catch (Throwable e) {
				throw new IOException("Unable to parse ivy descriptors", e);
			}

            // update the module list
            Map<ModuleName, IvyModule> modules = project.modules;
            synchronized (modules) {
                Map<ModuleName, IvyModule> old = new HashMap<ModuleName, IvyModule>(modules);
                List<IvyModule> sortedModules = new ArrayList<IvyModule>();

                modules.clear();
                for (IvyModuleInfo ivyDescriptor : ivyDescriptors) {
                    IvyModule mm = old.get(ivyDescriptor.name);
                    if (mm != null) {// found an existing matching module
                        if (debug)
                            logger.println("Reconfiguring " + mm);
                        mm.reconfigure(ivyDescriptor);
                        modules.put(ivyDescriptor.name, mm);
                    } else {// this looks like a new module
                        logger.println(Messages.IvyModuleSetBuild_DiscoveredModule(ivyDescriptor.name, ivyDescriptor.displayName));
                        mm = new IvyModule(project, ivyDescriptor, getNumber());
                        modules.put(mm.getModuleName(), mm);
                    }
                    sortedModules.add(mm);
                    mm.save();
                }

                // at this point the list contains all the live modules
                project.sortedActiveModules = sortedModules;

                // remaining modules are no longer active.
                old.keySet().removeAll(modules.keySet());
                for (IvyModule om : old.values()) {
                    if (debug)
                        logger.println("Disabling " + om);
                    om.makeDisabled(true);
                }
                modules.putAll(old);
            }

            // we might have added new modules
            Hudson.getInstance().rebuildDependencyGraph();

            // module builds must start with this build's number
            for (IvyModule m : modules.values())
                m.updateNextBuildNumber(getNumber());
        }

        @Override
        protected void post2(BuildListener listener) throws Exception {
            // asynchronous executions from the build might have left some
            // unsaved state,
            // so just to be safe, save them all.
            // TODO: uncomment when proxying stuff is done
//            for (IvyBuild b : getModuleLastBuilds().values())
//                b.save();

            if (project.isAggregatorStyleBuild()) {
                performAllBuildSteps(listener, project.getPublishers(), true);
            }
            performAllBuildSteps(listener, project.getProperties(), true);

            // aggregate all module fingerprints to us,
            // so that dependencies between module builds can be understood as
            // dependencies between module set builds.
            // TODO: we really want to implement this as a publisher,
            // but we don't want to ask for a user configuration, nor should it
            // show up in the persisted record.
            // IvyFingerprinter.aggregate(IvyModuleSetBuild.this);
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            if (project.isAggregatorStyleBuild()) {
                // schedule downstream builds. for non aggregator style builds,
                // this is done by each module
                scheduleDownstreamBuilds(listener);
                performAllBuildSteps(listener, project.getPublishers(), false);
            }

            performAllBuildSteps(listener, project.getProperties(), false);
        }
    }

    /**
     * Runs Ant and builds the project.
     *
     * This is only used for {@link IvyModuleSet#isAggregatorStyleBuild() the
     * aggregator style build}.
     */
    private static final class Builder extends IvyBuilder {
        private final Map<ModuleName,IvyBuildProxy2> proxies;
        private final Map<ModuleName,List<Publisher>> modulePublishers = new HashMap<ModuleName,List<Publisher>>();

        private IvyBuildProxy2 lastProxy;

        /**
         * Kept so that we can finalize them in the end method.
         */
        private final transient Map<ModuleName,ProxyImpl2> sourceProxies;

        public Builder(BuildListener listener,Map<ModuleName,ProxyImpl2> proxies, Collection<IvyModule> modules, List<String> goals, Map<String,String> systemProps) {
            super(listener,goals,systemProps);
            this.sourceProxies = proxies;
            this.proxies = new HashMap<ModuleName, IvyBuildProxy2>(proxies);
            for (Entry<ModuleName,IvyBuildProxy2> e : this.proxies.entrySet())
                e.setValue(new FilterImpl(e.getValue()));

            for (IvyModule m : modules)
                modulePublishers.put(m.getModuleName(),m.createModulePublishers());
        }

        private class FilterImpl extends IvyBuildProxy2.Filter<IvyBuildProxy2> implements Serializable {
            public FilterImpl(IvyBuildProxy2 core) {
                super(core);
            }

            @Override
            public void executeAsync(final BuildCallable<?,?> program) throws IOException {
                futures.add(Channel.current().callAsync(new AsyncInvoker(core,program)));
            }

            private static final long serialVersionUID = 1L;
        }

        /**
         * Invoked after the Ant has finished running, and in the master, not in the Ant process.
         */
        void end(Launcher launcher) throws IOException, InterruptedException {
            for (Map.Entry<ModuleName,ProxyImpl2> e : sourceProxies.entrySet()) {
                ProxyImpl2 p = e.getValue();
                for (Publisher publisher : modulePublishers.get(e.getKey())) {
                    // we'd love to do this when the module build ends, but doing so requires
                    // we know how many task segments are in the current build.
                    publisher.perform(p.owner(),launcher,listener);
                    p.appendLastLog();
                }
                p.close();
            }
        }

        @Override
        public Result call() throws IOException {
            try {
                return super.call();
            } finally {
                if(lastProxy!=null)
                    lastProxy.appendLastLog();
            }
        }

        @Override
        void preBuild(BuildEvent event) throws IOException, InterruptedException {
            // TODO
        }

        @Override
        void postBuild(BuildEvent event) throws IOException, InterruptedException {
            // TODO
        }

        @Override
        void preModule(BuildEvent event) throws InterruptedException, IOException, AbortException {
            File baseDir = event.getProject().getBaseDir();
            // TODO: find the module that contains this path?
//            ModuleName name = new ModuleName(event.getProject().getBaseDir());
//            IvyBuildProxy2 proxy = proxies.get(name);
//            listener.getLogger().flush();   // make sure the data until here are all written
//            proxy.start();
//            for (IvyReporter r : reporters.get(name))
//                if(!r.preBuild(proxy,event,listener))
//                    throw new AbortException(r+" failed");
        }

        @Override
        void postModule(BuildEvent event) throws InterruptedException, IOException, AbortException {
//            ModuleName name = new ModuleName(project);
//            IvyBuildProxy2 proxy = proxies.get(name);
//            List<IvyReporter> rs = reporters.get(name);
//            if(rs==null) { // probe for issue #906
//                throw new AssertionError("reporters.get("+name+")==null. reporters="+reporters+" proxies="+proxies);
//            }
//            for (IvyReporter r : rs)
//                if(!r.postBuild(proxy,event,listener))
//                    throw new hudson.maven.agent.AbortException(r+" failed");
//            listener.getLogger().flush();   // make sure the data until here are all written
//            proxy.end();
//            lastProxy = proxy;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Used to tunnel exception from Ant through remoting.
     */
    private static final class AntExecutionException extends RuntimeException {
        private AntExecutionException(Exception cause) {
            super(cause);
        }

        @Override
        public Exception getCause() {
            return (Exception) super.getCause();
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Executed on the slave to parse ivy.xml files and extract information into
     * {@link IvyModuleInfo}, which will be then brought back to the master.
     */
    private static final class IvyXmlParser implements Callable<List<IvyModuleInfo>, Throwable> {
        private static final String IVY_XML_PATTERN = "**/ivy.xml";
        private final BuildListener listener;
        /**
         * Capture the value of the static field so that the debug flag takes an
         * effect even when {@link IvyXmlParser} runs in a slave.
         */
        private final boolean verbose = debug;
        private final String ivyFilePattern;
        private final String ivyFileExcludePattern;
        private final String ivySettingsFile;
        private final String ivySettingsPropertyFiles;
        private final String ivyBranch;
        private final String workspace;
        private final String workspaceProper;

        public IvyXmlParser(BuildListener listener, IvyModuleSet project, String workspace) {
            // project cannot be shipped to the remote JVM, so all the relevant
            // properties need to be captured now.
            this.listener = listener;
            this.ivyFilePattern = project.getIvyFilePattern() == null ? IVY_XML_PATTERN : project.getIvyFilePattern();
            this.ivyFileExcludePattern = project.getIvyFileExcludesPattern();
            this.ivyBranch = project.getIvyBranch();
            this.workspace = workspace;
            this.ivySettingsFile = project.getIvySettingsFile();
            this.ivySettingsPropertyFiles = project.getIvySettingsPropertyFiles();
            this.workspaceProper = project.getLastBuild().getWorkspace().getRemote();
        }

		@SuppressWarnings("unchecked")
        public List<IvyModuleInfo> call() throws Throwable {
			File ws = new File(workspace);
            FileSet ivyFiles = Util.createFileSet(ws, ivyFilePattern, ivyFileExcludePattern);
            final PrintStream logger = listener.getLogger();

            Ivy ivy = getIvy(logger);
            HashMap<ModuleDescriptor, String> moduleDescriptors = new HashMap<ModuleDescriptor, String>();
            for (String ivyFilePath : ivyFiles.getDirectoryScanner().getIncludedFiles()) {
                final File ivyFile = new File(ws, ivyFilePath);

                ModuleDescriptor module = (ModuleDescriptor) ivy.execute(new IvyCallback() {
                    public Object doInIvyContext(Ivy ivy, IvyContext context) {
                        try {
                            return ModuleDescriptorParserRegistry.getInstance().parseDescriptor(ivy.getSettings(), ivyFile.toURI().toURL(),
                                    ivy.getSettings().doValidate());
                        } catch (MalformedURLException e) {
                            logger.println("The URL is malformed : " + ivyFile);
                            return null;
                        } catch (ParseException e) {
                            logger.println("Parsing error while reading the ivy file " + ivyFile);
                            return null;
                        } catch (IOException e) {
                            logger.println("I/O error while reading the ivy file " + ivyFile);
                            return null;
                        }
                    }
                });
                moduleDescriptors.put(module, ivyFilePath.replace('\\', '/'));
            }

            List<IvyModuleInfo> infos = new ArrayList<IvyModuleInfo>();
            List<ModuleDescriptor> sortedModuleDescriptors = ivy.sortModuleDescriptors(moduleDescriptors.keySet(), SortOptions.DEFAULT);
            for (ModuleDescriptor moduleDescriptor : sortedModuleDescriptors) {
                infos.add(new IvyModuleInfo(moduleDescriptor, moduleDescriptors.get(moduleDescriptor)));
            }

            if (verbose) {
                for (IvyModuleInfo moduleInfo : infos) {
                    logger.printf("Discovered module %s at %s.\n", moduleInfo.displayName, moduleInfo.relativePathToDescriptor);
                }
            }

            return infos;
        }

        /**
         *
         * @return the Ivy instance based on the {@link #ivyConfName}
         * @throws AbortException 
         *
         * @throws ParseException
         * @throws IOException
         */
        public Ivy getIvy(PrintStream logger) throws AbortException {
            Message.setDefaultLogger(new IvyMessageImpl());
            
            File settingsLoc = (ivySettingsFile == null) ? null : new File(workspaceProper, ivySettingsFile);

            if ((settingsLoc != null) && (!settingsLoc.exists())) {
                throw new AbortException(Messages.IvyModuleSetBuild_NoSuchIvySettingsFile(settingsLoc.getAbsolutePath()));
            }
            
            ArrayList<File> propertyFiles = new ArrayList<File>();
            if (StringUtils.isNotBlank(ivySettingsPropertyFiles)) {
                for (String file : ivySettingsPropertyFiles.split(",")) {
                    File propertyFile = new File(workspaceProper, file.trim());
                    if (!propertyFile.exists()) {
                        throw new AbortException(Messages.IvyModuleSetBuild_NoSuchPropertyFile(propertyFile.getAbsolutePath()));
                    }
                    propertyFiles.add(propertyFile);
                }
            }
            
            try {
                IvySettings ivySettings = new IvySettings();
                for (File file : propertyFiles) {
                    ivySettings.loadProperties(file);
                }
                if (settingsLoc != null) {
                    ivySettings.load(settingsLoc);
                    if (verbose)
                        logger.println("Configured Ivy using custom settings " + settingsLoc.getAbsolutePath());
                } else {
                    ivySettings.loadDefault();
                    if (verbose)
                        logger.println("Configured Ivy using default 2.1 settings");
                }
                if (ivyBranch != null) {
                    ivySettings.setDefaultBranch(ivyBranch);
                }
                return Ivy.newInstance(ivySettings);
            } catch (Exception e) {
                logger.println("Error while reading the default Ivy 2.1 settings: " + e.getMessage());
                logger.println(e.getStackTrace());
            }
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    private static final Logger LOGGER = Logger.getLogger(IvyModuleSetBuild.class.getName());

    /**
     * Extra verbose debug switch.
     */
    public static boolean debug = false;

    @Override
    public IvyModuleSet getParent() {// don't know why, but javac wants this
        return super.getParent();
    }

    private static final class IvyPreloadTask implements Callable<Boolean, IOException> {
		private static final long serialVersionUID = 1L;

		public Boolean call() throws IOException {
			try {
				return Channel.current().preloadJar(this, Ivy.class);
			} catch (InterruptedException e) {
			}
			return false;
		}
	}
}
