/**
 * Copyright 2007-2011 hibou, Martin Ficker, Jeffrey Metcalf, Timothy Bingaman
 *
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
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileNotFoundException;
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
import org.apache.ivy.core.settings.IvySettings;

import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.Message;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Trigger the build of other project based on the Ivy dependency management system.
 * 
 * @author jmetcalf@dev.java.net
 * @author martinficker@dev.java.net
 * @author hibou@dev.java.net
 * @author tbingaman@dev.java.net
 */
@SuppressWarnings("unchecked")
public class IvyBuildTrigger extends Notifier implements DependecyDeclarer {

    /**
     * The name of a copy of the ivy file relative to the projects root dir since the 
     * workspace may not always be accessible.
     */
    private static final String BACKUP_IVY_FILE_NAME = "ivy.xml";
    /**
     * The name of a copy of the ivy properties file relative to the projects root dir since the 
     * workspace may not always be accessible.
     */
    private static final String BACKUP_IVY_PROPERTIES_NAME = "ivy.properties";

    private static final Logger LOGGER = Logger.getLogger(IvyBuildTrigger.class.getName());

    /**
     * The name of the ivy file relative to the workspace as configured by the user.
     */
    private String ivyFile = "ivy.xml";
    /**
     * The name of the ivy properties file relative to the workspace as configured by the user.
     */
	private final String ivyProperties;

    /**
     * Identifies {@link IvyConfiguration} to be used.
     */
    private final String ivyConfName;
    
    private final boolean triggerWhenUnstable;
    
    private final boolean useUpstreamParameters;

    
    /**
     * The last modified time of the backup copy of the ivy file on the master.
     */
    private transient long lastmodified = 0;

    /**
     * The Ivy ModuleDescriptor for the project associated with this instance.
     */
    private transient ModuleDescriptor moduleDescriptor;

    /**
     * The Ivy VersionMatcher configured from the Ivy settings being used by this instance.
     */
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
     * @param ivyProperties
     *            the Ivy properties file path within the workspace
     * @param triggerWhenUnstable
     *            true if this build should be triggered even when an upstream build in Unstable.
     *            false if this build should be triggered only when an upstream build is Successful.
     * @param passPropertiesDownstream           
     */
    @DataBoundConstructor
    public IvyBuildTrigger(final String ivyFile, final String ivyConfName, final String ivyPropertiesFile, final boolean triggerWhenUnstable, final boolean useUpstreamParameters) {
        this.ivyFile = ivyFile;
        this.ivyConfName = ivyConfName;
        this.ivyProperties = ivyPropertiesFile;
        this.triggerWhenUnstable = triggerWhenUnstable;
        this.useUpstreamParameters = useUpstreamParameters;
    }

    /**
     * Constructor
     *
     * @param ivyFile
     *            the ivy.xml file path within the workspace
     * @param ivyConfName
     *            the Ivy configuration name to use
     * @deprecated use {@link #IvyBuildTrigger(String, String, boolean)} instead
     */
    @Deprecated
    public IvyBuildTrigger(final String ivyFile, final String ivyConfName) {
        this(ivyFile, ivyConfName, null, false, false);
    }

	public String getIvyPropertiesFile() {
		return ivyProperties;
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
     * @return the {@link IvyConfiguration} from the {@link #ivyConfName}
     */
    private IvyConfiguration getIvyConfiguration() {
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

    public boolean isTriggerWhenUnstable() {
        return triggerWhenUnstable;
    }

    public boolean isUseUpstreamParameters()
    {
        return useUpstreamParameters;
    }
    
    /**
     *
     * @return the Ivy instance based on the {@link #ivyConfName}
     *
     * @throws ParseException
     * @throws IOException
     */
    public Ivy getIvy(File localFilePath, String propertyFile) {
        Message.setDefaultLogger(new IvyMessageImpl());
        IvyConfiguration ivyConf = getIvyConfiguration();
            try {
                IvySettings ivySettings = new IvySettings();
                if (propertyFile != null) {
	                File f = new File(localFilePath,propertyFile);
	                ivySettings.loadProperties(f);
	                LOGGER.log(Level.INFO,"Configured Ivy using custom properties " + f);
                }
                if (ivyConf != null) {
                    ivySettings.load(new File(ivyConf.getIvyConfPath()));
                    LOGGER.log(Level.INFO,"Configured Ivy using custom settings " + ivyConf.getIvyConfPath());
                } else {
                    ivySettings.loadDefault();
                    LOGGER.log(Level.INFO,"Configured Ivy using default 2.1 settings");
                }
                return Ivy.newInstance(ivySettings);
            } 
            catch (Exception e) {
                LOGGER.log(Level.SEVERE,"Error while reading the default Ivy 2.1 settings: " + e.getMessage(),e);
            }
            return null;
    }

    /**
     * Get the last computed Ivy module descriptor created from the ivy.xml of
     * this trigger.
     * 
     * @param p the project this Trigger belongs to 
     * @return the Ivy module descriptor
     */
    private ModuleDescriptor getModuleDescriptor(AbstractProject p) {
        if (moduleDescriptor == null) {
            recomputeModuleDescriptor(p.getSomeBuildWithWorkspace());
        }
        return moduleDescriptor;
    }

    /**
     * Will only copy the file from the repository if its last modified time
     * exceeds what the instance thinks is the last recorded modified time of
     * the localFile, which is the local backup ivy file copy.  For this
     * to operate properly for remoting circumstances, the master and slave instances
     * must be reasonably time synchronized.
     * 
     * @param workspace Workspace root Directory
     * @param localFile The local file to be copied to
     * @return  true iff the file was actually copied
     * @throws IOException   If unable to access/copy the workspace ivy file
     * @throws InterruptedException  If interrupted while accessing the workspace ivy file
     */
    private boolean copyFileFromWorkspaceIfNecessary(FilePath workspace, String fileToCopy, File localFile, String localDestFile) throws IOException, InterruptedException {
        boolean copied = false;
        if (workspace != null) { // Unless the workspace is non-null we can not copy a new ivy file
    		FilePath f = workspace.child(fileToCopy);
    		// Copy the ivy file from the workspace (possibly at a slave) to the projects dir (at Master)
    		FilePath backupCopy = new FilePath(localFile).child(localDestFile);
    		long flastModified = f.lastModified();
    		if (flastModified == 0l) throw new FileNotFoundException("Can't stat file " + f);
    		if (flastModified > lastmodified) {
    			f.copyTo(backupCopy);
    			localFile.setLastModified(flastModified);
    			copied = true;
    			LOGGER.info("Copied the workspace file "+fileToCopy+" to backup "+localFile.getCanonicalFile().toString()+"/"+localDestFile);
    		}
        }
        return copied;
    }

    /**
     * Force the creation of the module descriptor.
     *
     * @param  b  a build this trigger belongs to
     */
    private void recomputeModuleDescriptor(AbstractBuild<?,?> b) {
        // The build may be null if no build with a workspace was found
        if (b == null) {
            return;
        }
        LOGGER.fine("Recomputing Moduledescriptor for Project "+b.getProject().getFullDisplayName());

		final File destDir = b.getProject().getRootDir();
		
		String propertyFileToLoadIntoIvy = null;
		
		String propertyFile = getIvyPropertiesFile();
		if (propertyFile != null && !propertyFile.trim().isEmpty()) {
			try {
	            copyFileFromWorkspaceIfNecessary(b.getWorkspace(), propertyFile, destDir, BACKUP_IVY_PROPERTIES_NAME);
	            propertyFileToLoadIntoIvy = BACKUP_IVY_PROPERTIES_NAME;
	        }
	        catch (IOException e) {
	            LOGGER.log(Level.WARNING, "Failed to access the workspace ivy properties file '"+propertyFile+"'", e);
	            LOGGER.log(Level.WARNING, "Removing ModuleDescriptor");
	            setModuleDescriptor(null);
	            return;
	        }
	        catch (InterruptedException e) {
	            LOGGER.log(Level.WARNING, "Interupted while accessing the workspace ivy properties file '"+propertyFile+"'", e);
	            File ivyP = new File(destDir, BACKUP_IVY_PROPERTIES_NAME);
	            if (ivyP.canRead()) {
	            	LOGGER.log(Level.WARNING, "Will try to use use existing ivy properties backup");
	            	propertyFileToLoadIntoIvy = BACKUP_IVY_PROPERTIES_NAME;
	        	}
	        }
		}

		Ivy ivy = getIvy(destDir, propertyFileToLoadIntoIvy);
        if (ivy == null) {
            setModuleDescriptor(null);
            return;
        }

        versionMatcher = ivy.getSettings().getVersionMatcher();

		String ivyDesc = getIvyFile();
        File ivyF = null;
        
		if (ivyDesc != null && !ivyDesc.trim().isEmpty()) {
	        ivyF = new File(destDir, BACKUP_IVY_FILE_NAME);
			try {
	            copyFileFromWorkspaceIfNecessary(b.getWorkspace(), ivyDesc, destDir, BACKUP_IVY_FILE_NAME);
	        }
	        catch (IOException e) {
	            LOGGER.log(Level.WARNING, "Failed to access the workspace ivy file '"+ivyDesc+"'", e);
	            LOGGER.log(Level.WARNING, "Removing ModuleDescriptor");
	            setModuleDescriptor(null);
	            return;
	        }
	        catch (InterruptedException e) {
	            LOGGER.log(Level.WARNING, "Interupted while accessing the workspace ivy file '"+ivyDesc+"'", e);
	            if (ivyF.canRead()) LOGGER.log(Level.WARNING, "Will try to use use existing ivy file backup");
	        }
		}

        final File fivyF = ivyF;

        // Calculate ModuleDescriptor from the backup copy 
        if (fivyF == null || !fivyF.canRead()) {
            LOGGER.log(Level.WARNING, "Cannot read ivy file backup...removing ModuleDescriptor");
            setModuleDescriptor(null);
            return;
        }

        if (moduleDescriptor == null || fivyF.lastModified() > lastmodified) {
            lastmodified = fivyF.lastModified();
            setModuleDescriptor((ModuleDescriptor) ivy.execute(new IvyCallback(){
                public Object doInIvyContext(Ivy ivy, IvyContext context) {
                    try {
                        return  ModuleDescriptorParserRegistry.getInstance().parseDescriptor(ivy.getSettings(),
                        		fivyF.toURI().toURL(), ivy.getSettings().doValidate());
                    } catch (MalformedURLException e) {
                        LOGGER.log(Level.WARNING, "The URL is malformed : " + fivyF, e);
                        return null;
                    } catch (ParseException e) {
                        LOGGER.log(Level.WARNING, "Parsing error while reading the ivy file " + fivyF, e);
                        return null;
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "I/O error while reading the ivy file " + fivyF, e);
                        return null;
                    }
                }
            }));
        }
    }

    /**
     * Set the Ivy ModuleDescriptor for this instance.  If the descriptor
     * has changed, then invalidate the descriptor project map and
     * rebuild the dependency graph.
     * 
     * @param d  The ModuleDescriptor to set.
     */
    private void setModuleDescriptor(ModuleDescriptor d) {
        ModuleDescriptor old = moduleDescriptor;
        moduleDescriptor = d;
        if (old == moduleDescriptor) return;
        if ((old==null) || !old.equals(moduleDescriptor)) {
            DESCRIPTOR.invalidateProjectMap();
            Hudson.getInstance().rebuildDependencyGraph();
         }
    }

    /**
     * The BuildStep perform implementation.  It performs a conditional recomputation of the project ModuleDescriptor.
     * 
     * @return  always returns true so the build can continue
     */
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        recomputeModuleDescriptor(build);
        return true;
    }

    /**
     * IvyBuildTrigger is only responsible for helping to build dependency graphs based on Ivy settings and configuration.
     * It never alters build status and its execution time should not be included in build time.
     * 
     * @return  always returns true meaning that the perform method will not run until the build is marked complete
     * @see #perform(AbstractBuild, Launcher, BuildListener)
     */
    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Build the dependency graph based on the Ivy ModuleDescriptor for this trigger.
     * 
     * @param   owner  the project this trigger belongs to
     * @param   graph  the DependencyGraph to which computed dependencies are added
     */
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        ModuleDescriptor md;
        md = getModuleDescriptor(owner);
        if (md == null) {
            return;
        }

        // Get All Dependencies from ivy.
        // Map them to corresponding  hudson projects

        DependencyDescriptor[] deps = md.getDependencies();

        for (DependencyDescriptor depDesc : deps) {
            ModuleId id = depDesc.getDependencyId();
            ModuleRevisionId rid = depDesc.getDependencyRevisionId();

            List<AbstractProject> possibleDeps = DESCRIPTOR.getProjectsFor (id);
            for (AbstractProject p : possibleDeps) {
                // ignore disabled Projects
                if (p.isDisabled()) p = null;
                // Such a project might not exist
                if (p != null && p instanceof Project) {
                    if (captures(rid, (Project) p)) {
                        graph.addDependency(new IvyThresholdDependency(p, owner, triggerWhenUnstable ? Result.UNSTABLE : Result.SUCCESS, useUpstreamParameters));
                    }
                }
            }
        }
    }

    /**
     * Determine if a dependency ModuleRevisionId captures the candidate project.
     * 
     * @param depRevId   The ModuleRevisionId of the dependency
     * @param candidate  A candidate Project that is tested against the depRevId
     * @return  true iff the candidate is captured according to the rules
     */
    private boolean captures(ModuleRevisionId depRevId, Project candidate) {
        IvyBuildTrigger t = (IvyBuildTrigger) candidate.getPublisher(DESCRIPTOR);
        boolean captures = (t != null); // check again in case candidate reconfigured against race condition
        if (captures && DESCRIPTOR.isExtendedVersionMatching()) {
            ModuleDescriptor cmd = t.getModuleDescriptor(candidate);
            ModuleRevisionId cmrid = cmd.getModuleRevisionId();

            VersionMatcher matcher = versionMatcher;

            // A null VersionMatcher means something is really wrong and we should not proceed to add the dependency
            captures = (matcher != null && matcher.isDynamic(depRevId));
            if (captures) {
                String dbranch = depRevId.getBranch();
                String cbranch = cmrid.getBranch();

                // Best Practice assumes you are using branch in your Ivy repository patterns before revision.
                // Otherwise you will need to use pattern-based (not latest.*) dynamic dependency revisions
                // to guarantee you will not run into Ivy resolve errors during builds.
                if (dbranch != null) { 
                    captures = dbranch.equals(cbranch);
                }
                else {
                    captures = (cbranch == null);
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
         * @return true if the configuration file exists
         */
        public boolean getExists() {
            return new File(ivyConfPath).exists();
        }
    }

    /**
     * The descriptor of this trigger.  It is annotated as an Extension so Hudson can automatically register this
     * instance as associated with IvyBuildTrigger.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * The descriptor implementation of this trigger
     */
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * Note that configurations are now called settings in Ivy 2.0
         * to avoid confusion with dependency configurations.
         */
        @CopyOnWrite
        private volatile IvyConfiguration[] configurations = new IvyConfiguration[0];

        /**
         * A map of Ivy ModuleIds against the associated AbstractProject instances.
         */
        @CopyOnWrite
        private transient volatile Map<ModuleId, List<AbstractProject>> projectMap=null;

        /**
         * This indicates if extendedVersionMatching should be used to help restrict
         * project dependency mapping.
         */
        private volatile boolean extendedVersionMatching = false;

        /**
         * Default constructor just loads any serialized configuration.
         */
        DescriptorImpl() {
            load();
        }

        /**
         * Return a List of AbstractProjects that have an IvyBuildtrigger configured with an
         * ivy file Matching the given ModuleID.  This method returns an empty list rather
         * than null when there are no matching projects.
         * 
         * @param id    The Module Id to search for
         * @return a List of Matching Projects
         */
        private List<AbstractProject> getProjectsFor(ModuleId searchId) {
            if (projectMap == null) calculateProjectMap();
            List<AbstractProject> result = projectMap.get(searchId);
            if (result == null) result = Collections.emptyList();
            return result;
        }

        /**
         * This method should be called if the cached Project Map may be invalid.
         * Reasons for this might be:
         * <ul>
         *  <li> An Ivy File has Changed
         *  <li> A Project was renamed
         * </ul>
         */
        private void invalidateProjectMap() {
            projectMap = null;
        }

        /**
         * Calculate the map of projects to Ivy ModuleId.
         */
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
                        LOGGER.log(Level.WARNING,"Calculating the ModuleDescriptor failed for project "+p.getFullDisplayName(),e);
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
         * @return  true if extended version matching is being used.
         */
        public boolean isExtendedVersionMatching() {
            return extendedVersionMatching;
        }

        /**
         * Implement the Descritor's display name.
         */
        public String getDisplayName() {
            return Messages.IvyBuildTrigger_DisplayName();
        }

        /**
         * Configure the Descriptor from a GUI request.
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            boolean r = true;

            int i;
            String[] names = req.getParameterValues("ivy_name");
            String[] paths = req.getParameterValues("ivy_conf_path");
            String vm = req.getParameter("ivy_version_matching");

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
            this.extendedVersionMatching = (vm != null);

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
         * The StaplerRequest parameter must include request parameters <code>org</code> and <code>name</code>
         * which respectively represent the Ivy module descriptor attributes <code>organisation</code> and <code>module</code>.
         * Optional request parameters that can be passed on the StaplerRequest include <code>branch</code> and <code>rev</code>
         * which respectively represent the Ivy module descriptor attributes <code>branch</code> and <code>revision</code>.
         * These values are used to match against the ModuleDescriptor of Hudson projects using the IvyBuildTrigger.  In the
         * case that more than one project matches, it is the first match that will win, and only that project will have
         * its downstream dependencies scheduled for builds.  Therefore the caller is wise to provide the most
         * information in the request to ensure the best possible match.  If the trigger descriptor is set to use extended
         * version matching, then at least one of the optional <code>rev</code> or <code>branch</code> is required on the request.
         * 
         * Note this event trigger does not actually build the matched project in Hudson.  It just schedules builds on downstream
         * dependent projects.  Successfully executing this event trigger requires global {@link Item#BUILD} permission on a
         * secured Hudson instance.
         * 
         * @author jmetcalf@dev.java.net
         * @param req  The StaplerRequest
         * @param rsp  The StaplerResponse
         * @throws IOException    IOException on the servlet call
         * @throws ServletException   ServletException on the servlet call
         * @see #isExtendedVersionMatching()
         */
        public void doHandleExternalTrigger(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            String org = Util.fixEmptyAndTrim(req.getParameter("org"));
            String name = Util.fixEmptyAndTrim(req.getParameter("name"));
            String branch = Util.fixEmptyAndTrim(req.getParameter("branch"));
            String rev = Util.fixEmptyAndTrim(req.getParameter("rev"));

            Hudson.getInstance().getACL().checkPermission(Item.BUILD);

            if (org == null || name == null)
                throw new IllegalArgumentException("doHandleExternalTrigger requires the org and name parameters");

            if (extendedVersionMatching && branch == null && rev == null)
                throw new IllegalArgumentException("doHandleExternalTrigger requires rev or branch when using extended revision matching");

            ModuleId id = ModuleId.newInstance(org, name);
            String ivylabel = ModuleRevisionId.newInstance(org, name, branch, Util.fixNull(rev)).toString();  // Used only in reporting.
            List<AbstractProject> downstream = Collections.emptyList();

            List<AbstractProject> candidates = getProjectsFor(id);
            for (AbstractProject candidate : candidates) {

                // Don't try to identify downstream dependencies of a disabled project.
                // This is more consistent when simulating Hudson dependency behavior,
                // i.e. a disabled parent could not have been built to initiate the trigger.
                if (candidate.isDisabled() || !(candidate instanceof Project)) continue;

                Project p = (Project) candidate;
                IvyBuildTrigger t = (IvyBuildTrigger) p.getPublisher(DESCRIPTOR);

                if (t != null) {
                    if (extendedVersionMatching) {
                        ModuleDescriptor md = t.getModuleDescriptor(p);
                        ModuleRevisionId mdrid = md.getModuleRevisionId();
                        String mdbranch = mdrid.getBranch();
                        if (branch != null && branch.equals(mdbranch) == false)
                            continue;
                        if (branch == null && mdbranch != null)
                            continue;
                        if (rev != null && rev.equals(mdrid.getRevision()) == false)
                            continue;
                    }
                    downstream = Hudson.getInstance().getDependencyGraph().getDownstream(p);
                    break;
                }
            }
            for (AbstractProject down : downstream) {
                if (down.isDisabled() == false) {
                    down.scheduleBuild(new UserCause(ivylabel));
                }
            }
        }

        /**
         * Check that the Ivy configuration file exists.
         *
         * @param req
         *            the file path
         */
        public FormValidation doCheckIvyConf(@QueryParameter final String value) {
            // this can be used to check the existence of a file on the server,
            // so needs to be protected
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) return FormValidation.ok();
            if (Util.fixEmpty(value) == null) {
                return FormValidation.error(Messages.IvyBuildTrigger_CheckIvyConf_PathRequiredError());
            }
            File f = new File(value);
            if (!f.isFile()) {
                return FormValidation.error(Messages.IvyBuildTrigger_CheckIvyConf_PathNotFileError(f));
            }

            return FormValidation.ok();
        }

        /**
         * Check that the workspace relative path to the ivy.xml file was entered and conforms to basic expectations.
         * This code cannot check for file existence since the file never exists for new projects that need to check
         * out the ivy file from source control. Under this condition the validator would always fail for first time
         * configuration which can be confusing.  The Ivy file might also be on a remote slave making checking for
         * existence at this level more difficult.
         *
         * @param value
         *            the relative path
         */
        public FormValidation doCheckIvyFile(@QueryParameter final String value) {
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) return FormValidation.ok();
            if (Util.fixEmpty(value) == null) {
                return FormValidation.error(Messages.IvyBuildTrigger_CheckIvyFile_PathRequiredError());
            }
            if (new File(value).isAbsolute()) {
                return FormValidation.error(Messages.IvyBuildTrigger_CheckIvyFile_PathAbsoluteError());
            }

            return FormValidation.ok();
        }

        /**
         * Check if this Descriptor is applicable to the job type.  Currently on instances of Project are supported.
         * 
         * @param   jobType   the AbstractProject type to be checked.
         * @return  true iff the project type can apply this Descriptor
         * @see  hudson.model.Project
         */
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return Project.class.isAssignableFrom(jobType);
        }
    }

    /**
     * This cause is used when triggering downstream builds from the external event trigger.
     * 
     * @author jmetcalf@dev.java.net
     * @see #doHandleExternalTrigger(StaplerRequest, StaplerResponse)
     */
    public static class UserCause extends Cause.UserCause {

        private String ivylabel;

        /**
         * Constructor
         * 
         * @param ivylabel  The Ivy ModuleRevisionId label computed from the StaplerRequest.
         */
        public UserCause(String ivylabel) {
            this.ivylabel = ivylabel;
        }

        /**
         * 
         * @return the Ivy ModuleRevisionId label associated with the cause.
         */
        public String getIvylabel() {
            return ivylabel;
        }

        /**
         * 
         * @return  the short description of the cause for display purposes.
         */
        @Override
        public String getShortDescription() {
            return Messages.IvyBuildTrigger_UserCause_ShortDescription(getIvylabel(), getUserName());
        }
    }
}
