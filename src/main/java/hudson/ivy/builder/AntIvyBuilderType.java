/*
 * The MIT License
 * 
 * Copyright (c) 2010-2011, Timothy Bingaman
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
package hudson.ivy.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.ivy.AntIvyBuildWrapper;
import hudson.ivy.IvyModuleSet;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.tasks.Ant;
import hudson.tasks.Builder;
import hudson.tasks.Ant.AntInstallation;

public class AntIvyBuilderType extends IvyBuilderType {
    private String targets;
    /**
     * Identifies {@link AntInstallation} to be used.
     */
    private String antName;
    /**
     * ANT_OPTS if not null.
     */
    private String antOpts;
    /**
     * Optional build script path relative to the workspace. Used for the Ant '-f' option.
     */
    private String buildFile;
    /**
     * Optional properties to be passed to Ant. Follows {@link Properties} syntax.
     */
    private String antProperties;

    @DataBoundConstructor
    public AntIvyBuilderType(String antName, String buildFile, String targets, String antProperties, String antOpts) {
        this.antName = antName;
        this.buildFile = buildFile;
        this.targets = targets;
        this.antProperties = antProperties;
        this.antOpts = antOpts;
    }

    public String getTargets() {
        return targets;
    }

    protected String getCalculatedTargets(String targets, List<Environment> buildEnvironments) {
        String additionalTargets = getAdditionalAntTargets(buildEnvironments);
        if (StringUtils.isNotBlank(additionalTargets)) {
            return StringUtils.isNotBlank(targets) ? additionalTargets + targets : additionalTargets;
        }
        return targets;
    }

    public String getAntName() {
        return antName;
    }

    public String getAntOpts() {
        return antOpts;
    }

    /**
     * Possibly null, whitespace-separated (including TAB, NL, etc) VM options to be used to launch Ant process.
     * <p/>
     * If antOpts is null or empty, we'll return the globally-defined ANT_OPTS. Also prepend any build-specific
     * ANT_OPTS.
     *
     * @param environment
     */
    protected String getCalculatedAntOpts(List<Environment> buildEnvironments) {
        String antOpts = null;
        if ((this.antOpts != null) && (this.antOpts.trim().length() > 0)) {
            antOpts = this.antOpts.replaceAll("[\t\r\n]+", " ");
        } else {
            String globalOpts = IvyModuleSet.DESCRIPTOR.getGlobalAntOpts();
            if (globalOpts != null) {
                antOpts = globalOpts.replaceAll("[\t\r\n]+", " ");
            }
        }
        String additionalArgs = getAddtionalAntOpts(buildEnvironments);
        if (StringUtils.isNotBlank(additionalArgs)) {
            return StringUtils.isNotBlank(antOpts) ? additionalArgs + antOpts : additionalArgs;
        } else {
            return antOpts;
        }
    }

    public String getBuildFile() {
        return buildFile;
    }

    public String getAntProperties() {
        return antProperties;
    }

    @Override
    public Map<String, String> getEnvironment() {
        Map<String, String> envs = new HashMap<String, String>();
        String opts = getCalculatedAntOpts(null);
        if (opts != null) {
            envs.put("ANT_OPTS", opts);
        }
        return envs;
    }

    @Override
    public Builder getBuilder(Properties additionalProperties, String overrideTargets, List<Environment> environments) {
        StringBuilder properties = new StringBuilder();
        if (antProperties != null) {
            properties.append(antProperties);
        }

        if (additionalProperties != null) {
            for (String key : additionalProperties.stringPropertyNames()) {
                properties.append("\n");
                properties.append(key).append("=").append(additionalProperties.getProperty(key));
            }
        }
        return new Ant(getCalculatedTargets(overrideTargets == null ? targets : overrideTargets, environments), antName,
                getCalculatedAntOpts(environments), buildFile, properties.length() == 0 ? null : properties.toString());
    }

    @Extension
    public static class DescriptorImpl extends IvyBuilderTypeDescriptor {

        @Override
        public String getDisplayName() {
            return "Ant Builder";
        }

        public Ant.AntInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
        }

    }

    private String getAdditionalAntTargets(List<Environment> buildEnvironments) {
        if (buildEnvironments != null) {
            StringBuilder additionalAntTargets = new StringBuilder();
            for (Environment environment : buildEnvironments) {
                if (environment instanceof AntIvyBuildWrapper.AntIvyBuilderEnvironment) {
                    additionalAntTargets.append(((AntIvyBuildWrapper.AntIvyBuilderEnvironment) environment).getAdditionalArgs())
                            .append(" ");
                }
            }
            return additionalAntTargets.toString();
        }
        return null;
    }

    private String getAddtionalAntOpts(List<Environment> buildEnvironments) {
        if (buildEnvironments != null) {
            StringBuilder addtionalAntOpts = new StringBuilder();
            for (Environment environment : buildEnvironments) {
                if (environment instanceof AntIvyBuildWrapper.AntIvyBuilderEnvironment) {
                    addtionalAntOpts.append(((AntIvyBuildWrapper.AntIvyBuilderEnvironment) environment).getAdditionalOpts())
                            .append(" ");
                }
            }
            return addtionalAntOpts.toString();
        }
        return null;
    }
}
