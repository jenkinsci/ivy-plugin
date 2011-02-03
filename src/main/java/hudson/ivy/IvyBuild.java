/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Timothy Bingaman
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

import static hudson.model.Result.FAILURE;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.slaves.WorkspaceList;
import hudson.slaves.WorkspaceList.Lease;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildEvent;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link Run} for {@link IvyModule}.
 *
 * @author Timothy Bingaman
 */
public class IvyBuild extends AbstractIvyBuild<IvyModule, IvyBuild> {
    /**
     * {@link IvyReporter}s that will contribute project actions. Can be null if
     * there's none.
     */
    /* package */List<IvyReporter> projectActionReporters;

    public IvyBuild(IvyModule job) throws IOException {
        super(job);
    }

    public IvyBuild(IvyModule job, Calendar timestamp) {
        super(job, timestamp);
    }

    public IvyBuild(IvyModule project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    if (ancs.get(i - 1).getObject() instanceof IvyModuleSetBuild) {
                        // if under IvyModuleSetBuild, "up" means IMSB
                        return ancs.get(i - 1).getUrl() + '/';
                    }
                }
            }
        }
        return super.getUpUrl();
    }

    @Override
    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req != null) {
            List<Ancestor> ancs = req.getAncestors();
            for (int i = 1; i < ancs.size(); i++) {
                if (ancs.get(i).getObject() == this) {
                    if (ancs.get(i - 1).getObject() instanceof IvyModuleSetBuild) {
                        // if under IvyModuleSetBuild, display the module name
                        return getParent().getDisplayName();
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * Gets the {@link IvyModuleSetBuild} that has the same build number.
     *
     * @return null if no such build exists, which happens when the module build
     *         is manually triggered.
     * @see #getModuleSetBuild()
     */
    public IvyModuleSetBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }

    /**
     * Gets the "governing" {@link IvyModuleSet} that has set the workspace
     * for this build.
     *
     * @return null if no such build exists, which happens if the build is
     *         manually removed.
     * @see #getParentBuild()
     */
    public IvyModuleSetBuild getModuleSetBuild() {
        return getParent().getParent().getNearestOldBuild(getNumber());
    }

    @Override
    public ChangeLogSet<? extends Entry> getChangeSet() {
        return new FilteredChangeLogSet(this);
    }

    /**
     * We always get the changeset from {@link IvyModuleSetBuild}.
     */
    @Override
    public boolean hasChangeSetComputed() {
        return true;
    }

    public void registerAsProjectAction(IvyReporter reporter) {
        if (projectActionReporters == null)
            projectActionReporters = new ArrayList<IvyReporter>();
        projectActionReporters.add(reporter);
    }

    @Override
    public void run() {
        addAction(new IvyModuleEnvironmentAction());
        run(new RunnerImpl());

        getProject().updateTransientActions();

        IvyModuleSetBuild parentBuild = getModuleSetBuild();
        if (parentBuild != null)
            parentBuild.notifyModuleBuild(this);
    }

    /**
     * Backdoor for {@link IvyModuleSetBuild} to assign workspaces for modules.
     */
    @Override
    protected void setWorkspace(FilePath path) {
        super.setWorkspace(path);
    }

    /**
     * Runs Ant/Ivy and builds the project.
     */
    private static final class Builder extends IvyBuilder {
        private final IvyBuildProxy buildProxy;
        private final IvyReporter[] reporters;

        private long startTime;

        public Builder(BuildListener listener, IvyBuildProxy buildProxy, IvyReporter[] reporters, List<String> goals, Map<String, String> systemProps) {
            super(listener, goals, systemProps);
            this.buildProxy = new FilterImpl(buildProxy);
            this.reporters = reporters;
        }

        private class FilterImpl extends IvyBuildProxy.Filter<IvyBuildProxy> implements Serializable {
            public FilterImpl(IvyBuildProxy buildProxy) {
                super(buildProxy);
            }

            @Override
            public void executeAsync(final BuildCallable<?, ?> program) throws IOException {
                futures.add(Channel.current().callAsync(new AsyncInvoker(core, program)));
            }

            private static final long serialVersionUID = 1L;
        }

        @Override
        void preBuild(BuildEvent event) throws IOException, InterruptedException {
            for (IvyReporter r : reporters)
                r.preBuild(buildProxy, event, listener);
        }

        @Override
        void postBuild(BuildEvent event) throws IOException, InterruptedException {
            for (IvyReporter r : reporters)
                r.postBuild(buildProxy, event, listener);
        }

        @Override
        void preModule(BuildEvent event) throws InterruptedException, IOException, AbortException {
            for (IvyReporter r : reporters)
                if (!r.enterModule(buildProxy, event, listener))
                    throw new AbortException(r + " failed");
        }

        @Override
        void postModule(BuildEvent event) throws InterruptedException, IOException, AbortException {
            for (IvyReporter r : reporters)
                if (!r.leaveModule(buildProxy, event, listener))
                    throw new AbortException(r + " failed");
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link IvyBuildProxy} implementation.
     */
    class ProxyImpl implements IvyBuildProxy, Serializable {
        public <V, T extends Throwable> V execute(BuildCallable<V, T> program) throws T, IOException, InterruptedException {
            return program.call(IvyBuild.this);
        }

        /**
         * This method is implemented by the remote proxy before the invocation
         * gets to this. So correct code shouldn't be invoking this method on
         * the master ever.
         *
         * @deprecated This helps IDE find coding mistakes when someone tries to
         *             call this method.
         */
        @Deprecated
        public final void executeAsync(BuildCallable<?, ?> program) throws IOException {
            throw new AssertionError();
        }

        public FilePath getRootDir() {
            return new FilePath(IvyBuild.this.getRootDir());
        }

        public FilePath getProjectRootDir() {
            return new FilePath(IvyBuild.this.getParent().getRootDir());
        }

        public FilePath getModuleSetRootDir() {
            return new FilePath(IvyBuild.this.getParent().getParent().getRootDir());
        }

        public FilePath getArtifactsDir() {
            return new FilePath(IvyBuild.this.getArtifactsDir());
        }

        public void setResult(Result result) {
            IvyBuild.this.setResult(result);
        }

        public Calendar getTimestamp() {
            return IvyBuild.this.getTimestamp();
        }

        public long getMilliSecsSinceBuildStart() {
            return System.currentTimeMillis() - getTimestamp().getTimeInMillis();
        }

        public boolean isArchivingDisabled() {
            return IvyBuild.this.getParent().getParent().isArchivingDisabled();
        }

        public void registerAsProjectAction(IvyReporter reporter) {
            IvyBuild.this.registerAsProjectAction(reporter);
        }

        public void registerAsAggregatedProjectAction(IvyReporter reporter) {
            IvyModuleSetBuild pb = getParentBuild();
            if (pb != null)
                pb.registerAsProjectAction(reporter);
        }

        private Object writeReplace() {
            return Channel.current().export(IvyBuildProxy.class, this);
        }
    }

    class ProxyImpl2 extends ProxyImpl implements IvyBuildProxy2 {
        private final SplittableBuildListener listener;
        long startTime;
        private final OutputStream log;
        private final IvyModuleSetBuild parentBuild;

        ProxyImpl2(IvyModuleSetBuild parentBuild, SplittableBuildListener listener) throws FileNotFoundException {
            this.parentBuild = parentBuild;
            this.listener = listener;
            log = new FileOutputStream(getLogFile()); // no buffering so that
                                                      // AJAX clients can see
                                                      // the log live
        }

        public void start() {
            onStartBuilding();
            startTime = System.currentTimeMillis();
            try {
                listener.setSideOutputStream(log);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void end() {
            if (result == null)
                setResult(Result.SUCCESS);
            onEndBuilding();
            duration = System.currentTimeMillis() - startTime;
            parentBuild.notifyModuleBuild(IvyBuild.this);
            try {
                listener.setSideOutputStream(null);
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Sends the accumuldated log in {@link SplittableBuildListener} to the
         * log of this build.
         */
        public void appendLastLog() {
            try {
                listener.setSideOutputStream(log);
                listener.setSideOutputStream(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Performs final clean up. Invoked after the entire aggregator build is
         * completed.
         */
        protected void close() {
            try {
                log.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (hasntStartedYet()) {
                // Mark the build as aborted. This method is used when the
                // aggregated build
                // failed before it didn't even get to this module.
                run(new Runner() {
                    @Override
                    public Result run(BuildListener listener) {
                        listener.getLogger().println(Messages.IvyBuild_FailedEarlier());
                        return Result.NOT_BUILT;
                    }

                    @Override
                    public void post(BuildListener listener) {
                    }

                    @Override
                    public void cleanUp(BuildListener listener) {
                    }
                });
            }
        }

        /**
         * Gets the build for which this proxy is created.
         */
        public IvyBuild owner() {
            return IvyBuild.this;
        }

        private Object writeReplace() {
            // when called from remote, methods need to be executed in the
            // proper Executor's context.
            return Channel.current().export(IvyBuildProxy2.class, Executor.currentExecutor().newImpersonatingProxy(IvyBuildProxy2.class, this));
        }
    }

    private class RunnerImpl extends AbstractRunner {
        private List<Publisher> reporters;

        @Override
        protected Lease decideWorkspace(Node n, WorkspaceList wsl) throws InterruptedException, IOException {
            return wsl.allocate(getModuleSetBuild().getModuleRoot().child(getProject().getRelativePathToModuleRoot()));
        }

        @Override
        protected Result doRun(BuildListener listener) throws Exception {
            // pick up a list of reporters to run
            reporters = getProject().createModulePublishers();
            if (debug)
                listener.getLogger().println("Reporters=" + reporters);
            if (!preBuild(listener, reporters))
                return FAILURE;

            Result r = null;
            try {
                List<BuildWrapper> wrappers = new ArrayList<BuildWrapper>(getProject().getBuildWrappersList().toList());

                ParametersAction parameters = getAction(ParametersAction.class);
                if (parameters != null)
                    parameters.createBuildWrappers(IvyBuild.this, wrappers);

                for (BuildWrapper w : wrappers) {
                    Environment e = w.setUp(IvyBuild.this, launcher, listener);
                    if (e == null)
                        return (r = FAILURE);
                    buildEnvironments.add(e);
                }

                hudson.tasks.Builder builder = getProject().getParent().getIvyBuilderType()
                        .getBuilder(null, getProject().getTargets(), buildEnvironments);
                if (!builder.perform(IvyBuild.this, launcher, listener))
                    r = FAILURE;
            } finally {
                if (r != null)
                    setResult(r);
                // tear down in reverse order
                boolean failed = false;
                for (int i = buildEnvironments.size() - 1; i >= 0; i--) {
                    if (!buildEnvironments.get(i).tearDown(IvyBuild.this, listener)) {
                        failed = true;
                    }
                }
                // WARNING The return in the finally clause will trump any return before
                if (failed)
                    return FAILURE;
            }

            return r;
        }

        @Override
        public void post2(BuildListener listener) throws Exception {
            if (!performAllBuildSteps(listener, reporters, true))
                setResult(FAILURE);
            if (!performAllBuildSteps(listener, project.getProperties(), true))
                setResult(FAILURE);
        }

        @Override
        public void cleanUp(BuildListener listener) throws Exception {
            // at this point it's too late to mark the build as a failure, so ignore return value.
            performAllBuildSteps(listener, reporters,false);
            performAllBuildSteps(listener, project.getProperties(),false);
            scheduleDownstreamBuilds(listener);
        }
    }

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;

    @Override
    public IvyModule getParent() {// don't know why, but javac wants this
        return super.getParent();
    }

    public static class IvyModuleEnvironmentAction implements EnvironmentContributingAction {
        public String getUrlName() {
            return null;
        }

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
            env.put("IVY_MODULE_NAME", ((IvyModule) build.getParent()).getModuleName().name);
            env.put("IVY_MODULE_ORGANISATION", ((IvyModule) build.getParent()).getModuleName().organisation);
        }
    }
}
