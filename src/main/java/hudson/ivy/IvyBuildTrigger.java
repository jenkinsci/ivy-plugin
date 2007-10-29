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

import fr.jayasoft.ivy.DependencyDescriptor;
import fr.jayasoft.ivy.Ivy;
import fr.jayasoft.ivy.ModuleDescriptor;
import fr.jayasoft.ivy.ModuleId;
import fr.jayasoft.ivy.parser.ModuleDescriptorParserRegistry;
import hudson.CopyOnWrite;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.tasks.Publisher;
import hudson.util.FormFieldValidator;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Trigger the build of other project based on the Ivy dependency managment
 * system.
 */
public class IvyBuildTrigger extends Publisher {

    private final String ivyFile;

    /**
     * Identifies {@link IvyConfiguration} to be used.
     */
    private final String ivyConfName;

    private transient ModuleDescriptor md;

    /**
     * Contructor
     * 
     * @param ivyFile
     *            the ivy.xml file path within the workspace
     * @param ivyConfName
     *            the Ivy configuration name to use
     */
    public IvyBuildTrigger(final String ivyFile, final String ivyConfName) {
        this.ivyFile = ivyFile;
        this.ivyConfName = ivyConfName;
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
    public IvyConfiguration getIvyConfiguration() {
        for (IvyConfiguration i : DESCRIPTOR.getConfigurations()) {
            if (ivyConfName != null && i.getName().equals(ivyConfName)) {
                return i;
            }
        }
        return null;
    }

    /**
     * 
     * @return the Ivy instance based on the {@link #ivyConfName}
     * 
     * @throws ParseException
     * @throws IOException
     */
    public Ivy getIvy() throws ParseException, IOException {
        IvyConfiguration ivyConf = getIvyConfiguration();
        File conf = new File(ivyConf.getIvyConfPath());
        Ivy ivy = new Ivy();
        ivy.configure(conf);
        return ivy;
    }

    /**
     * Get the last computed Ivy module descriptior created from the ivy.xml of
     * this trigger
     * 
     * @return the Ivy module descriptior
     * @throws ParseException
     * @throws IOException
     */
    public ModuleDescriptor getModuleDescriptor() throws ParseException, IOException {
        if (md == null) {
            recomputeModuleDescriptor();
        }
        return md;
    }

    /**
     * Force the creation of the module descriptor from the ivy.xml file
     * 
     * @throws ParseException
     * @throws IOException
     */
    public void recomputeModuleDescriptor() throws ParseException, IOException {
        Ivy ivy = getIvy();
        if (ivy == null) {
            md = null;
        } else {
            File ivyF = new File(ivyFile);
            md = ModuleDescriptorParserRegistry.getInstance().parseDescriptor(ivy, ivyF.toURI().toURL(),
                    ivy.doValidate());
        }
    }

    /**
     * Container the configuration of Ivy
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
    public boolean prebuild(Build build, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        try {
            recomputeModuleDescriptor();
        } catch (ParseException e) {
            logger.println("Unable to parse the ivy file : " + e.getMessage());
            e.printStackTrace(logger);
        } catch (IOException e) {
            logger.println("Unable to parse the ivy file : " + e.getMessage());
            e.printStackTrace(logger);
        }
        return true;
    }

    public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException,
            IOException {

        PrintStream logger = listener.getLogger();

        try {
            Ivy ivy;
            ivy = getIvy();
            if (ivy == null) {
                return true;
            }

            List<Project> projects = Hudson.getInstance().getAllItems(Project.class);
            Map<ModuleId, ModuleDescriptor> moduleIdMap = new HashMap<ModuleId, ModuleDescriptor>();
            Map<ModuleId, AbstractProject<?, ?>> projectMap = new HashMap<ModuleId, AbstractProject<?, ?>>();
            for (AbstractProject<?, ?> p : projects) {
                if (p.isDisabled()) {
                    continue;
                }
                IvyBuildTrigger t = (IvyBuildTrigger) build.getProject().getPublisher(DESCRIPTOR);
                if (t != null) {
                    ModuleId id = t.getModuleDescriptor().getModuleRevisionId().getModuleId();
                    moduleIdMap.put(id, t.getModuleDescriptor());
                    projectMap.put(id, p);
                }
            }

            Set<ModuleDescriptor> dependencies = new HashSet<ModuleDescriptor>();
            processFilterNodeFromRoot(getModuleDescriptor(), dependencies, moduleIdMap);

            @SuppressWarnings("unchecked")
            List<ModuleDescriptor> sortedModules = ivy.sortModuleDescriptors(dependencies);
            List<AbstractProject<?, ?>> deps = new ArrayList<AbstractProject<?, ?>>(sortedModules.size());
            for (ModuleDescriptor m : sortedModules) {
                deps.add(projectMap.get(m));
            }

            // if (!build.getResult().isWorseThan(getThreshold())) {
            for (AbstractProject p : deps) {
                if (p.isDisabled()) {
                    logger.println(p.getName() + " is disabled. Triggering skiiped");
                    continue;
                }

                // this is not completely accurate, as a new build might be
                // triggered
                // between these calls
                String name = p.getName() + " #" + p.getNextBuildNumber();
                if (!p.scheduleBuild()) {
                    logger.println("Triggering a new build of " + name);
                } else {
                    logger.println(name + " is already in the queue");
                }
            }
            // }

        } catch (ParseException e) {
            logger.println("Unable to parse the ivy file : " + e.getMessage());
            e.printStackTrace(logger);
        }

        return true;
    }

    /**
     * Adds the current node to the toKeep collection and then processes the
     * each of the direct dependencies of this node that appear in the
     * moduleIdMap (indicating that the dependency is part of this BuildList)
     * 
     * @param node
     *            the node to be processed
     * @param toKeep
     *            the set of ModuleDescriptors that should be kept
     * @param moduleIdMap
     *            reference mapping of moduleId to ModuleDescriptor that are
     *            part of the BuildList
     */
    private void processFilterNodeFromRoot(ModuleDescriptor node, Set<ModuleDescriptor> toKeep,
            Map<ModuleId, ModuleDescriptor> moduleIdMap) {
        toKeep.add(node);

        DependencyDescriptor[] deps = node.getDependencies();
        for (int i = 0; i < deps.length; i++) {
            ModuleId id = deps[i].getDependencyId();
            if (moduleIdMap.get(id) != null) {
                processFilterNodeFromRoot(moduleIdMap.get(id), toKeep, moduleIdMap);
            }
        }
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

        @CopyOnWrite
        private volatile IvyConfiguration[] configurations = new IvyConfiguration[0];

        DescriptorImpl() {
            super(IvyBuildTrigger.class);
            load();
        }

        /**
         * 
         * @return every existing configuration
         */
        public IvyConfiguration[] getConfigurations() {
            return configurations;
        }

        @Override
        public String getDisplayName() {
            return "Trigger the build of other project based on the Ivy management system";
        }

        @Override
        protected void convert(Map<String, Object> oldPropertyBag) {
            if (oldPropertyBag.containsKey("configurations")) {
                configurations = (IvyConfiguration[]) oldPropertyBag.get("configurations");
            }
        }

        @Override
        public boolean configure(StaplerRequest req) {
            boolean r = true;

            int i;
            String[] names = req.getParameterValues("ivy_name");
            String[] paths = req.getParameterValues("ivy_conf_path");

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


            this.configurations = confs;

            save();

            return r;
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
                        error("The Ivy configuration path is required");
                        return;
                    }
                    if (!f.isFile()) {
                        error(f + " is not a file");
                        return;
                    }

                    // I couldn't come up with a simple logic to test for a
                    // maven installation
                    // there seems to be just too much difference between m1 and
                    // m2.

                    ok();
                }
            }.process();
        }


        /**
         * Check that the ivy.xml file exist
         * 
         * @param req
         *            the Stapler request
         * @param rsp
         *            the Stapler response
         * @throws IOException
         * @throws ServletException
         */
        public void doCheckIvyFile(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            // this can be used to check the existence of a file on the server,
            // so needs to be protected
            new FormFieldValidator(req, rsp, true) {
                @Override
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if (f.getPath().equals("")) {
                        error("The Ivy file path is required");
                        return;
                    }
                    if (!f.isFile()) {
                        error(f + " is not a file");
                        return;
                    }

                    // I couldn't come up with a simple logic to test for a
                    // maven installation
                    // there seems to be just too much difference between m1 and
                    // m2.

                    ok();
                }
            }.process();
        }

        @Override
        public Publisher newInstance(StaplerRequest req) {
            return new IvyBuildTrigger(req.getParameter("ivy_file"), req.getParameter("ivy_conf_name"));
        }
    }

}
