/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman
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

import hudson.CopyOnWrite;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Resource;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.DependencyGraph.Dependency;
import hudson.model.Descriptor.FormException;
import hudson.model.queue.CauseOfBlockage;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.LogRotator;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link Job} that builds projects based on Ivy.
 *
 * @author Timothy Bingaman
 */
public final class IvyModule extends AbstractIvyProject<IvyModule, IvyBuild> implements Saveable {
    private static final String IVY_XML_PATH = "ivy.xml";

    private DescribableList<Publisher, Descriptor<Publisher>> publishers = new DescribableList<Publisher, Descriptor<Publisher>>(this);

    /**
     * Name taken from {@link ModuleRevisionId#getName()}.
     */
    private String displayName;

    /**
     * Revision number of this module as of the last build, taken from
     * {@link ModuleRevisionId#getRevision()}.
     *
     * This field can be null if Hudson loaded old data that didn't record this
     * information, so that situation needs to be handled gracefully.
     */
    private String revision;

    /**
     * Ivy branch of this module as of the last build, taken from
     * {@link ModuleRevisionId#getBranch()}.
     *
     * This field can be null if Hudson loaded old data that didn't record this
     * information, so that situation needs to be handled gracefully.
     */
    private String ivyBranch;

    private transient ModuleName moduleName;

    /**
     * Relative path from the workspace to the ivy descriptor file for this
     * module.
     *
     * Strings like "ivy.xml" (if the ivy.xml file is checked out directly in
     * the workspace), "abc/ivy.xml", "foo/bar/zot/ivy.xml".
     */
    private String relativePathToDescriptorFromWorkspace;

    /**
     * If this module has targets specified by itself. Otherwise leave it null
     * to use the default targets specified in the parent.
     */
    private String targets;

    /**
     * Relative path from the workspace to the ivy descriptor file for this
     * module.
     *
     * Strings like "ivy.xml" (if the ivy.xml file is directly in
     * the module root), "ivy/ivy.xml", "build/ivy.xml".
     */
    private String relativePathToDescriptorFromModuleRoot;

    /**
     * List of modules that this module declares direct dependencies on.
     */
    @CopyOnWrite
    private volatile Set<ModuleDependency> dependencies;

    /* package */IvyModule(IvyModuleSet parent, IvyModuleInfo moduleInfo, int firstBuildNumber) throws IOException {
        super(parent, moduleInfo.name.toFileSystemName());
        reconfigure(moduleInfo);
        updateNextBuildNumber(firstBuildNumber);
    }

    /**
     * {@link IvyModule} follows the same log rotation schedule as its parent.
     */
    @Override
    public LogRotator getLogRotator() {
        return getParent().getLogRotator();
    }

    /**
     * @deprecated Not allowed to configure log rotation per module.
     */
    @Deprecated
    @Override
    public void setLogRotator(LogRotator logRotator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsLogRotator() {
        return false;
    }

    @Override
    public boolean isBuildable() {
        // not buildable if the parent project is disabled
        return super.isBuildable() && getParent().isBuildable();
    }

    /**
     * Called to update the module with the new ivy.xml information.
     * <p>
     * This method is invoked on {@link IvyModule} that has the matching
     * {@link ModuleName}.
     */
    /* package */final void reconfigure(IvyModuleInfo moduleInfo) {
        this.displayName = moduleInfo.displayName;
        this.revision = moduleInfo.revision;
        this.ivyBranch = moduleInfo.branch;
        this.relativePathToDescriptorFromWorkspace = moduleInfo.relativePathToDescriptor;
        this.dependencies = moduleInfo.dependencies;
        disabled = false;
    }

    @Override
    protected void doSetName(String name) {
        moduleName = ModuleName.fromFileSystemName(name);
        super.doSetName(moduleName.toString());
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        if (publishers == null)
            publishers = new DescribableList<Publisher, Descriptor<Publisher>>(this);
        publishers.setOwner(this);
        if (dependencies == null) {
            dependencies = Collections.emptySet();
        }
    }

    /**
     * Relative path to this module's root directory from the workspace of a
     * {@link IvyModuleSet}.
     *
     * The path separator is normalized to '/'.
     */
    public String getRelativePath() {
        return relativePathToDescriptorFromWorkspace;
    }

    /**
     * Gets the revision number in the ivy.xml file as of the last build.
     *
     * @return This method can return null if Hudson loaded old data that didn't
     *         record this information, so that situation needs to be handled
     *         gracefully.
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Gets the Ivy branch in the ivy.xml file as of the last build.
     *
     * @return This method can return null if Hudson loaded old data that didn't
     *         record this information, so that situation needs to be handled
     *         gracefully.
     */
    public String getIvyBranch() {
        return ivyBranch;
    }

    /**
     * Gets the list of targets to execute for this module.
     */
    public String getTargets() {
        return targets;
    }

    public String getRelativePathToDescriptorFromModuleRoot() {
        if (relativePathToDescriptorFromModuleRoot != null)
            return relativePathToDescriptorFromModuleRoot;
        return getParent().getRelativePathToDescriptorFromModuleRoot();
    }

    public String getUserConfiguredRelativePathToDescriptorFromModuleRoot() {
        return relativePathToDescriptorFromModuleRoot;
    }

    public String getRelativePathToModuleRoot() {
        return StringUtils.removeEnd(relativePathToDescriptorFromWorkspace, StringUtils.defaultString(getRelativePathToDescriptorFromModuleRoot(),
                IVY_XML_PATH));
    }

    @Override
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        if (getParent().isAggregatorStyleBuild()) {
            return publishers;
        }

        DescribableList<Publisher, Descriptor<Publisher>> publishersList = new DescribableList<Publisher, Descriptor<Publisher>>(Saveable.NOOP);
        try {
            publishersList.addAll(createModulePublishers());
        } catch (IOException e) {
            LOGGER.warning("Failed to load module publisher list");
        }
        return publishersList;
    }

    @Override
    public JDK getJDK() {
        // share one setting for the whole module set.
        return getParent().getJDK();
    }

    @Override
    protected Class<IvyBuild> getBuildClass() {
        return IvyBuild.class;
    }

    @Override
    protected IvyBuild newBuild() throws IOException {
        return super.newBuild();
    }

    public ModuleName getModuleName() {
        return moduleName;
    }

    /**
     * Gets organisation+name+revision as {@link ModuleDependency}.
     */
    public ModuleDependency asDependency() {
        return new ModuleDependency(moduleName, Functions.defaulted(revision, ModuleDependency.UNKNOWN), Functions.defaulted(ivyBranch,
                ModuleDependency.UNKNOWN));
    }

    @Override
    public String getShortUrl() {
        return moduleName.toFileSystemName() + '/';
    }

    @Exported(visibility = 2)
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getPronoun() {
        return Messages.IvyModule_Pronoun();
    }

    @Override
    public boolean isNameEditable() {
        return false;
    }

    @Override
    public IvyModuleSet getParent() {
        return (IvyModuleSet) super.getParent();
    }

    /**
     * {@link IvyModule} uses the workspace of the {@link IvyModuleSet}, so it
     * always needs to be built on the same slave as the parent.
     */
    @Override
    public Label getAssignedLabel() {
        Node n = getParent().getLastBuiltOn();
        if (n == null)
            return null;
        return n.getSelfLabel();
    }

    /**
     * Workspace of a {@link IvyModule} is a part of the parent's workspace.
     * <p>
     * That is, {@Link IvyModuleSet} builds are incompatible with any
     * {@link IvyModule} builds, whereas {@link IvyModule} builds are compatible
     * with each other.
     *
     * @deprecated as of 1.319 in {@link AbstractProject}.
     */
    @Deprecated
    @Override
    public Resource getWorkspaceResource() {
        return new Resource(getParent().getWorkspaceResource(), getDisplayName() + " workspace");
    }

    @Override
    public boolean isFingerprintConfigured() {
        return true;
    }

    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
        if (isDisabled() || (getParent().ignoreUpstreamChanges() && getParent().isAggregatorStyleBuild()))
            return;
        
        Map<ModuleDependency, IvyModule> modules = new HashMap<ModuleDependency, IvyModule>();
        if (!getParent().ignoreUpstreamChanges()) {
            for (IvyModule m : Hudson.getInstance().getAllItems(IvyModule.class)) {
                if (m.isDisabled())
                    continue;
                modules.put(m.asDependency(), m);
                modules.put(m.asDependency().withUnknownRevision(), m);
            }
        }

        // Even if ignoreUpstreamChanges is true we still need to calculate the
        // dependencies between the modules of this project. Also, in case two
        // modules with the same name are defined, modules in the same
        // IvyModuleSet takes precedence.

        for (IvyModule m : getParent().getModules()) {
            if (m.isDisabled())
                continue;
            modules.put(m.asDependency(), m);
            modules.put(m.asDependency().withUnknownRevision(), m);
        }

        // if the build style is the aggregator build, define dependencies
        // against project,
        // not module.
        AbstractProject downstream = getParent().isAggregatorStyleBuild() ? getParent() : this;

        for (ModuleDependency d : dependencies) {
            IvyModule src = modules.get(d);
            if (src == null)
                src = modules.get(d.withUnknownRevision());
            if (src == null)
                continue;

            AbstractProject upstream;
            if (src.getParent().isAggregatorStyleBuild()) {
                upstream = src.getParent();
            } else {
                // Add a virtual dependency from the parent project to the
                // downstream one to make the
                // "Block build when upstream project is building" option behave
                // properly
                if (!this.getParent().equals(src.getParent()) && !hasDependency(graph, src.getParent(), downstream))
                    graph.addDependency(new IvyVirtualDependency(src.getParent(), downstream));
                upstream = src;
            }

            // Create the build dependency, ignoring self-referencing or already existing deps
            if (upstream != downstream && !hasDependency(graph, upstream, downstream))
                graph.addDependency(new IvyThresholdDependency(upstream, downstream, Result.SUCCESS));
        }
    }

    private boolean hasDependency(DependencyGraph graph, AbstractProject upstream, AbstractProject downstream) {
        for (Dependency dep : graph.getDownstreamDependencies(upstream)) {
            if (dep instanceof IvyDependency && dep.getDownstreamProject().equals(downstream))
                return true;
        }
        return false;
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        CauseOfBlockage cob = super.getCauseOfBlockage();
        if (cob != null)
            return cob;

        if (!getParent().isAggregatorStyleBuild()) {
            DependencyGraph graph = Hudson.getInstance().getDependencyGraph();
            for (AbstractProject tup : graph.getTransitiveUpstream(this)) {
                if(getParent() == tup.getParent() && (tup.isBuilding() || tup.isInQueue()))
                        return new BecauseOfUpstreamModuleBuildInProgress(tup);
            }
        }

        return null;
    }

    /**
     * Because the upstream module build is in progress, and we are configured to wait for that.
     */
    public static class BecauseOfUpstreamModuleBuildInProgress extends CauseOfBlockage {
        public final AbstractProject<?,?> up;

        public BecauseOfUpstreamModuleBuildInProgress(AbstractProject<?,?> up) {
            this.up = up;
        }

        @Override
        public String getShortDescription() {
            return Messages.IvyModule_UpstreamModuleBuildInProgress(up.getName());
        }
    }

    @Override
    protected void addTransientActionsFromBuild(IvyBuild build, Set<Class> added) {
        if (build == null)
            return;
        List<IvyReporter> list = build.projectActionReporters;
        if (list == null)
            return;

        for (IvyReporter step : list) {
            if (!added.add(step.getClass()))
                continue; // already added
            try {
                Action a = step.getProjectAction(this);
                if (a != null)
                    transientActions.add(a);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to getProjectAction from " + step + ". Report issue to plugin developers.", e);
            }
        }
    }

    /**
     * List of active {@link Publisher}s configured for this module.
     */
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishers() {
        return publishers;
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        targets = Util.fixEmptyAndTrim(req.getParameter("targets"));
        relativePathToDescriptorFromModuleRoot = Util.fixEmptyAndTrim(req.getParameter("relativePathToDescriptorFromModuleRoot"));

        publishers.rebuild(req,req.getSubmittedForm(),BuildStepDescriptor.filter(Publisher.all(),this.getClass()));

        // dependency setting might have been changed by the user, so rebuild.
        Hudson.getInstance().rebuildDependencyGraph();
    }

    @Override
    protected void performDelete() throws IOException, InterruptedException {
        super.performDelete();
         getParent().onModuleDeleted(this);
    }

    /**
     * Creates a list of {@link Publisher}s to be used for a build of this project.
     */
    protected final List<Publisher> createModulePublishers() {
        List<Publisher> modulePublisherList = new ArrayList<Publisher>();

        getPublishers().addAllTo(modulePublisherList);
        if (!getParent().isAggregatorStyleBuild()) {
            getParent().getPublishers().addAllTo(modulePublisherList);
        }

        return modulePublisherList;
    }

    private static final Logger LOGGER = Logger.getLogger(IvyModule.class.getName());
}
