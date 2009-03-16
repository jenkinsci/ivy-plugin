/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.ivy;

import hudson.CopyOnWrite;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Project;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidator;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.ivy.Ivy;
import org.apache.ivy.Ivy.IvyCallback;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.Message;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Trigger the build of other project based on the Ivy dependency managment
 * system.
 */
public class IvyBuildTrigger extends Publisher implements DependecyDeclarer {

    /**
     * The name of a copy of the ivy file relative to the projects root dir since the 
     * workspace may not always be accessible.
     */
    private static final String BACKUP_IVY_FILE_NAME = "ivy.xml";

    private static final Logger LOGGER = Logger.getLogger(IvyBuildTrigger.class.getName());

    /**
     * The name of the ivy file relative to the workspace as configured by the user.
     */
    private String ivyFile = "ivy.xml";

    private long lastmodified = 0;

    /**
     * Identifies {@link IvyConfiguration} to be used.
     */
    private final String ivyConfName;

    /**
     * Indicates whether or not dependent project candidates will be filtered using
     * an extended fine grained strategy based on branch and/or revision.
     */
    private boolean extendedVersionMatching = false;

    private transient ModuleDescriptor moduleDescriptor;

    private transient VersionMatcher versionMatcher;

    /**
     * Set the Message Implementation for ivy to avoid logging to err
     */
    static {
        Message.setDefaultLogger(new IvyMessageImpl());
    }

    /**
     * Constructor
     *
     * @param ivyFile
     *            the ivy.xml file path within the workspace
     * @param ivyConfName
     *            the Ivy configuration name to use
     * @param extendedVersionMatching
     *            indicates if using extended version matching to determine project dependencies
     */
    public IvyBuildTrigger(final String ivyFile, final String ivyConfName, final boolean extendedVersionMatching) {
        this.ivyFile = ivyFile;
        this.ivyConfName = ivyConfName;
        this.extendedVersionMatching = extendedVersionMatching;
    }

    /**
     *
     * @return the ivy.xml file path within the workspace
     */
    public String getIvyFile() {
        return ivyFile;
    }

    /**
     *
     * @return the Ivy configuration name used
     */
    public String getIvyConfName() {
        return ivyConfName;
    }

    /**
     * 
     * @return  true if using extended version matching
     */
    public boolean isExtendedVersionMatching() {
        return extendedVersionMatching;
    }

    /**
     *
     * @return the {@link IvyConfiguration} from the {@link #ivyConfName}
     */
    public IvyConfiguration getIvyConfiguration() {
        IvyConfiguration conf = null;
        if (ivyConfName != null) {
            for (IvyConfiguration i : DESCRIPTOR.getConfigurations()) {
                if (i.getName().equals(ivyConfName)) {
                    conf = i;
                    break;
                }
            }
        }
        return conf;
    }

    /**
     *
     * @return the Ivy instance based on the {@link #ivyConfName}
     *
     * @throws ParseException
     * @throws IOException
     */
    public Ivy getIvy() {
        Message.setDefaultLogger(new IvyMessageImpl());
        IvyConfiguration ivyConf = getIvyConfiguration();
        Ivy ivy = Ivy.newInstance();
        Ivy configured = null;
        if (ivyConf != null) {
            try {
                ivy.configure(new File(ivyConf.getIvyConfPath()));
                LOGGER.info("Configured Ivy using the Ivy settings " + ivyConf.getName());
                configured = ivy;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error while reading the Ivy settings " + ivyConf.getName()
                        + " at " + ivyConf.getIvyConfPath(), e);
            }
        }
        else {
            try {
                ivy.configureDefault();
                LOGGER.info("Configured Ivy using default 2.0 settings");
                configured = ivy;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error while reading the default Ivy 2.0 settings", e);
            }
        }
        return configured;
    }

    /**
     * Get the last computed Ivy module descriptor created from the ivy.xml of
     * this trigger
     * @param p the project this Trigger belongs to 
     * @return the Ivy module descriptor
     */
    public ModuleDescriptor getModuleDescriptor(AbstractProject p) {
        if (moduleDescriptor == null) {
            recomputeModuleDescriptor(p);
        }
        return moduleDescriptor;
    }

    /**
     * Returns the configured Ivy VersionMatcher associated with this trigger.
     * 
     * @return The Ivy VersionMatcher associated with this trigger.
     */
    public VersionMatcher getVersionMatcher() {
        return versionMatcher;
    }

    /**
     * Force the creation of the module descriptor
     *
     * @throws ParseException
     * @throws IOException
     */
    private void recomputeModuleDescriptor(AbstractProject p) {
        LOGGER.fine ("Recomputing Moduledescriptor for Project "+p.getFullDisplayName());
        Ivy ivy = getIvy();
        if (ivy == null) {
            setModuleDescriptor(null);
            return;
        }
        versionMatcher = ivy.getSettings().getVersionMatcher();
        copyIvyFileFromWorkspaceIfNecessary (p);
        // Calculate Moduledescriptor from the backupcopy 
        final File ivyF = new File(p.getRootDir(), BACKUP_IVY_FILE_NAME);
        if (!ivyF.exists()) {
            setModuleDescriptor(null);
            return;
        }
        if (moduleDescriptor != null && ivyF.lastModified() == lastmodified) return;
        lastmodified = ivyF.lastModified();
        setModuleDescriptor((ModuleDescriptor) ivy.execute(new IvyCallback(){
            public Object doInIvyContext(Ivy ivy, IvyContext context) {
                try {
                    return  ModuleDescriptorParserRegistry.getInstance().parseDescriptor(ivy.getSettings(),
                            ivyF.toURI().toURL(), ivy.getSettings().doValidate());
                } catch (MalformedURLException e) {
                    LOGGER.log(Level.WARNING, "The URL is malformed : " + ivyF, e);
                    return null;
                } catch (ParseException e) {
                    LOGGER.log(Level.WARNING, "Parsing error while reading the ivy file " + ivyF, e);
                    return null;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "I/O error while reading the ivy file " + ivyF, e);
                    return null;
                }
            }
        }));
    }

    private void setModuleDescriptor (ModuleDescriptor d) {
        ModuleDescriptor old = moduleDescriptor;
        moduleDescriptor = d;
        if (old == moduleDescriptor) return;
        if ((old==null) || !old.equals(moduleDescriptor)) {
            DESCRIPTOR.invalidateProjectMap();
            Hudson.getInstance().rebuildDependencyGraph();
        }
    }

    /**
     * Container for the Ivy configuration.
     * Note that configurations are now called settings in Ivy 2.0
     * to avoid confusion with dependency configurations.
     */
    public static class IvyConfiguration {

        private String name;

        private String ivyConfPath;

        /**
         * Contructor
         *
         * @param name
         *            the name of the configuration
         * @param ivyConfPath
         *            the full path to the ivy configuration file
         */
        public IvyConfiguration(String name, String ivyConfPath) {
            this.name = name;
            this.ivyConfPath = ivyConfPath;
        }

        /**
         *
         * @return the full path to the ivy configuration file
         */
        public String getIvyConfPath() {
            return ivyConfPath;
        }

        /**
         *
         * @return the name of the configuration
         */
        public String getName() {
            return name;
        }

        /**
         *
         * @return <code>true</code> if the configuration file exists
         */
        public boolean getExists() {
            return new File(ivyConfPath).exists();
        }
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        recomputeModuleDescriptor(build.getProject());
        return true;
    }

    /**
     * Workaround for a bug in p.getWorkspace prior to 1.279
     * 
     * @param p
     * @return
     */
    private static FilePath getWorkspace (AbstractProject p) {
        try {
            return p.getWorkspace();
        } catch (NullPointerException e) {
            LOGGER.warning("Caught a problem in AbstractProject.getWorkspace!" );
            return null;
        }
    }

    /**
     * 
     * @param workspace Workspace root Directory
     * @param rootDir The Projects root dir where the backup copy is stored
     */
    private void copyIvyFileFromWorkspaceIfNecessary(AbstractProject p) {
        FilePath workspace = getWorkspace (p);
        File rootDir = p.getRootDir();
        if (workspace == null) return; // If the workspace is null we can not copy a new ivy file
        FilePath f = workspace.child(ivyFile);
        try {
            // TODO: We could possible avoid copying the file each time
            // Copy the ivy file from the workspace (possible at a slave) to the projects dir (at Master)
            FilePath backupCopy = new FilePath (
                    new File (rootDir, BACKUP_IVY_FILE_NAME));
            f.copyTo (backupCopy);
            new File (rootDir, BACKUP_IVY_FILE_NAME).setLastModified(f.lastModified());
            return;
        } catch (IOException e) {
            LOGGER.warning("Failed to read the ivy file " + f);
        } catch (InterruptedException e) {
            LOGGER.warning("Interupted when reading the ivy file " + f);
        }
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        ModuleDescriptor md;
        md = getModuleDescriptor(owner);
        if (md == null) {
            return;
        }

        // Get All Dependencies from ivy.
        // Map them to corresponding  hudson projects

        DependencyDescriptor[] deps = md.getDependencies();
        List <AbstractProject> dependencies = new ArrayList<AbstractProject>();

        for (DependencyDescriptor depDesc : deps) {
            ModuleId id = depDesc.getDependencyId();
            ModuleRevisionId rid = depDesc.getDependencyRevisionId();

            List<AbstractProject> possibleDeps = DESCRIPTOR.getProjectsFor (id);
            for (AbstractProject p : possibleDeps) {
                // ignore disabled Projects
                if (p.isDisabled()) p = null;
                // Such a project might not exist
                if (p != null && p instanceof Project) {
                    if (captures(true, rid, (Project) p)) {
                        dependencies.add(p);
                    }
                }
            }
        }
        graph.addDependency(dependencies, owner);
    }

    private boolean captures(boolean enforceDynamicMatching, ModuleRevisionId depRevId, Project candidate) {
        IvyBuildTrigger t = (IvyBuildTrigger) candidate.getPublisher(DESCRIPTOR);
        boolean captures = (t != null); // check again in case candidate reconfigured against race condition
        if (captures && extendedVersionMatching) {
            ModuleDescriptor cmd = t.getModuleDescriptor(candidate);
            ModuleRevisionId cmrid = cmd.getModuleRevisionId();

            VersionMatcher matcher = versionMatcher;

            // A null VersionMatcher means something is really wrong and we should not proceed to add the dependency
            captures = (matcher != null);
            if (captures && enforceDynamicMatching) {
                captures = matcher.isDynamic(depRevId);
            }
            if (captures) {
                String dbranch = depRevId.getBranch();
                if (dbranch != null) {
                    // Best Practice assumes you are using branch in your Ivy repository patterns before revision.
                    // Otherwise you will need to use pattern-based (not latest.*) dynamic dependency revisions
                    // to guarantee you will not run into Ivy resolve errors during builds. 
                    String cbranch = cmrid.getBranch();
                    captures = cbranch != null && cbranch.equals(dbranch);
                }
                if (captures) {
                    captures = matcher.accept(depRevId, cmrid);
                    if (captures && matcher.needModuleDescriptor(depRevId, cmrid)) {
                        captures = matcher.accept(depRevId, cmd);
                    }
                }
            }
        }
        return captures;
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * The descriptor of this trigger
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * The descriptor implementation of this trigger
     */
    public static final class DescriptorImpl extends Descriptor<Publisher> {

        /**
         * Note that configurations are now called settings in Ivy 2.0
         * to avoid confusion with dependency configurations.
         */
        @CopyOnWrite
        private volatile IvyConfiguration[] configurations = new IvyConfiguration[0];

        @CopyOnWrite
        private transient volatile Map<ModuleId, List<AbstractProject>> projectMap=null;

        private volatile boolean globalExtendedVersionMatching = false;

        DescriptorImpl() {
            super(IvyBuildTrigger.class);
            load();
        }

        /**
         * Return a List of AbstractProjects that have an IvyBuildtrigger configured with an
         * ivy file Matching the given ModuleID
         * @param id    The Module Id to search for
         * @return a List of Matching Projects
         */
        public List<AbstractProject> getProjectsFor(ModuleId searchId) {
            if (projectMap == null) calculateProjectMap();
            List<AbstractProject> result = projectMap.get(searchId);
            if (result == null) result = Collections.emptyList();
            return result;
        }

        /**
         * This method should be called if the cached Project Map may be invalid.
         * Reason for this maybe:
         * <ul>
         *  <li> An Ivy File has Changed
         *  <li> A Project was renamed
         * </ul>
         */
        public void invalidateProjectMap() {
            projectMap = null;
        }

        private void calculateProjectMap() {
            List<Project> projects = Hudson.getInstance().getAllItems(Project.class);
            Map<ModuleId, List<AbstractProject>> projectMap = new HashMap<ModuleId, List<AbstractProject>>();
            for (Project<?, ?> p : projects) {
                if (p.isDisabled()) {
                    continue;
                }
                IvyBuildTrigger t = (IvyBuildTrigger) p.getPublisher(DESCRIPTOR);
                if (t != null) {
                    ModuleDescriptor m;

                    try {
                        m = t.getModuleDescriptor(p);
                    } catch (Exception e) { // This does sometimes fail with an exception instead of returning null for an offline slave
                        LOGGER.log(Level.WARNING,"Calculating the Modules Descriptor failed for project "+p.getFullDisplayName(),e);
                        m =  null;
                    }

                    if (m != null) {
                        ModuleId id = m.getModuleRevisionId().getModuleId();
                        List<AbstractProject> list = projectMap.get (id);
                        if (list == null) {
                            list = new ArrayList<AbstractProject> ();
                        }
                        list.add(p);
                        projectMap.put(id, list);
                    }
                }
            }
            this.projectMap = projectMap;
        }

        /**
         *
         * @return every existing configuration
         */
        public IvyConfiguration[] getConfigurations() {
            return configurations;
        }

        /**
         * 
         * @return  is extended version matching to be configured globally?
         */
        public boolean isGlobalExtendedVersionMatching() {
            return globalExtendedVersionMatching;
        }

        @Override
        public String getDisplayName() {
            return Messages.IvyBuildTrigger_DisplayName();
        }

        @Override
        protected void convert(Map<String, Object> oldPropertyBag) {
            if (oldPropertyBag.containsKey("configurations")) {
                configurations = (IvyConfiguration[]) oldPropertyBag.get("configurations");
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            boolean r = true;

            int i;
            String[] names = req.getParameterValues("ivy_name");
            String[] paths = req.getParameterValues("ivy_conf_path");
            String gvm = req.getParameter("ivy_global_version_matching");

            IvyConfiguration[] confs;

            if (names != null && paths != null) {
                int len = Math.min(names.length, paths.length);
                confs = new IvyConfiguration[len];
                for (i = 0; i < len; i++) {
                    if (Util.nullify(names[i]) == null) {
                        continue;
                    }
                    if (Util.nullify(paths[i]) == null) {
                        continue;
                    }
                    confs[i] = new IvyConfiguration(names[i], paths[i]);
                }
            } else {
                confs = new IvyConfiguration[0];
            }
            LOGGER.info("IvyConfigurations: " + confs.length);

            this.configurations = confs;
            this.globalExtendedVersionMatching = (gvm != null);

            if (globalExtendedVersionMatching) {
                List<Project> projects = Hudson.getInstance().getAllItems(Project.class);
                for (Project<?, ?> p : projects) {
                    if (p.isDisabled()) {
                        continue;
                    }
                    IvyBuildTrigger t = (IvyBuildTrigger) p.getPublisher(DESCRIPTOR);
                    if (t != null) {
                        if (! t.extendedVersionMatching) {
                            t.extendedVersionMatching = true;
                            try {
                                p.save();
                            }
                            catch (IOException io) {
                                LOGGER.log(Level.WARNING, "Failed to save " + p.getConfigFile(), io);
                            }
                        }
                    }
                }
            }

            save();
            invalidateProjectMap();
            Hudson.getInstance().rebuildDependencyGraph();
            return r;
        }

        /**
         * Simulate what would happen internally if a successful build occurred on an IvyBuildTrigger managed project.
         * This provides a hook to start builds on downstream projects if a triggering event outside of Hudson occurred.
         * One such triggering event could be publish of a non-integration (milestone/release) build of the IvyBuildTrigger
         * managed project code to the Ivy repository that is visible to your build system.
         * 
         * The StaplerRequest parameter must include request parameters <code>org</code>, <code>name</code>,
         * and <code>rev</code> which respectively represent the Ivy module descriptor attributes
         * <code>organisation</code>, <code>module</code>, and <code>revision</code>.  If extended revision matching
         * is not being used, then the revision attribute is ignored by the trigger even though required in the request.
         * The value of <code>branch</code> is optional on the request and may be used to match against the module
         * descriptor <code>branch</code> attribute when present.  These attributes are used to match against projects
         * managed with the IvyBuildTrigger and need to be set accordingly.
         * 
         * Note this event does not actually build the matched project(s) in Hudson.  It just schedules builds on downstream
         * dependent projects.  Successfully executing this event requires global {@link Item#BUILD} permission on a
         * secured instance.
         * 
         * @author jmetcalf
         * @param req  The StaplerRequest
         * @param rsp  The StaplerResponse
         * @throws IOException    IOException on the servlet call
         * @throws ServletException   ServletException on the servlet call
         */
        public void doHandleExternalTrigger(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String org = Util.fixEmptyAndTrim(req.getParameter("org"));
            String name = Util.fixEmptyAndTrim(req.getParameter("name"));
            String branch = Util.fixEmptyAndTrim(req.getParameter("branch"));
            String rev = Util.fixEmptyAndTrim(req.getParameter("rev"));

            Hudson.getInstance().getACL().checkPermission(Item.BUILD);

            if (org == null || name == null || rev == null)
                throw new IllegalArgumentException("doHandleExternalTrigger requires the org, name, and rev parameters be non-empty");

            final ModuleRevisionId rid = ModuleRevisionId.newInstance(org, name, branch, rev);

            List<AbstractProject> candidates = getProjectsFor(rid.getModuleId());
            for (AbstractProject candidate : candidates) {

                // Don't try to identify downstream dependencies of a disabled project.
                // This is more consistent when simulating Hudson dependency behavior,
                // i.e. a disabled parent could not have been built to initiate the trigger.
                if (candidate.isDisabled() || !(candidate instanceof Project)) continue;

                Project p = (Project) candidate;
                IvyBuildTrigger t = (IvyBuildTrigger) p.getPublisher(DESCRIPTOR);

                if (t != null && t.captures(false, rid, p)) {
                    List<AbstractProject> downstream = Hudson.getInstance().getDependencyGraph().getDownstream(p);
                    for (AbstractProject down : downstream) {
                        if (down.isDisabled() == false) {
                            down.scheduleBuild(new UserCause(rid));
                        }
                    }
                }
            }
        }

        /**
         * Check that the Ivy configuration file exist
         *
         * @param req
         *            the Stapler request
         * @param rsp
         *            the Stapler response
         * @throws IOException
         * @throws ServletException
         */
        public void doCheckIvyConf(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this can be used to check the existence of a file on the server,
            // so needs to be protected
            new FormFieldValidator(req, rsp, true) {
                @Override
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if (f.getPath().equals("")) {
                        error(Messages.IvyBuildTrigger_CheckIvyConf_PathRequiredError());
                        return;
                    }
                    if (!f.isFile()) {
                        error(Messages.IvyBuildTrigger_CheckIvyConf_PathNotFileError(f));
                        return;
                    }

                    ok();
                }
            }.process();
        }

        /**
         * Check that the workspace relative path to the ivy.xml file was entered.  This code cannot check for
         * file existence since the file never exists for new projects that need to check out the ivy file
         * from source control. Under this condition the validator would always fail for first time configuration
         * which can be confusing.
         *
         * @param req
         *            the Stapler request
         * @param rsp
         *            the Stapler response
         * @throws IOException
         * @throws ServletException
         */
        public void doCheckIvyFile(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req, rsp, true) {
                @Override
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if (f.getPath().equals("")) {
                        error(Messages.IvyBuildTrigger_CheckIvyFile_PathRequiredError());
                        return;
                    }
                    if (f.isAbsolute()) {
                        error(Messages.IvyBuildTrigger_CheckIvyFile_PathAbsoluteError());
                        return;
                    }

                    ok();
                }
            }.process();
        }

        @Override
        public Publisher newInstance(StaplerRequest req) {
            boolean ivyVersionMatching = globalExtendedVersionMatching || (req.getParameter("ivy_version_matching") != null);
            return new IvyBuildTrigger(req.getParameter("ivy_file"), req.getParameter("ivy_conf_name"), ivyVersionMatching);
        }
    }

    /**
     * This cause is used when triggering downstream builds from the external event trigger.
     * 
     * @author jmetcalf
     * @see #doHandleExternalTrigger(StaplerRequest, StaplerResponse)
     */
    public static class UserCause extends Cause.UserCause {

        private ModuleRevisionId rid;

        /**
         * Constructor
         * 
         * @param rid  The ModuleRevisionId computed from the StaplerRequest.
         */
        public UserCause(ModuleRevisionId rid) {
            this.rid = rid;
        }

        public ModuleRevisionId getModuleRevisionId() {
            return rid;
        }

        @Override
        public String getShortDescription() {
            return Messages.IvyBuildTrigger_UserCause_ShortDescription(getModuleRevisionId(), getUserName());
        }
    }
}
