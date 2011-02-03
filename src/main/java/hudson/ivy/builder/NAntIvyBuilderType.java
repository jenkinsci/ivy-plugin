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

import hudson.model.Environment;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.plugins.nant.NantBuilder;
import hudson.plugins.nant.NantBuilder.NantInstallation;
import hudson.tasks.Builder;

public class NAntIvyBuilderType extends IvyBuilderType {
    private String targets;
    /**
     * Identifies {@link NantBuilder} to be used.
     */
    private String nantName;
    /**
     * NANT_OPTS if not null.
     */
    private String nantOpts;
    /**
     * Optional build script path relative to the workspace. Used for the Ant
     * '-f' option.
     */
    private String buildFile;
    /**
     * Optional properties to be passed to Ant. Follows {@link Properties}
     * syntax.
     */
    private String nantProperties;

    @DataBoundConstructor
    public NAntIvyBuilderType(String nantName, String buildFile, String targets, String nantProperties, String nantOpts) {
        this.nantName = nantName;
        this.buildFile = buildFile;
        this.targets = targets;
        this.nantProperties = nantProperties;
        this.nantOpts = nantOpts;
    }

    public String getTargets() {
        return targets;
    }

    public String getNantName() {
        return nantName;
    }

    public String getNantOpts() {
        return nantOpts;
    }

    public String getBuildFile() {
        return buildFile;
    }

    public String getNantProperties() {
        return nantProperties;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return new HashMap<String, String>();
    }

    @Override
    public Builder getBuilder(Properties additionalProperties, String overrideTargets, List<Environment> environments) {
        StringBuilder properties = new StringBuilder();
        
        if (nantProperties != null)
            properties.append(nantProperties);

        if (additionalProperties != null) {
            for (String key : additionalProperties.stringPropertyNames()) {
                properties.append("\n");
                properties.append(key).append("=").append(additionalProperties.getProperty(key));
            }
        }
        return new NantBuilder(buildFile, nantName, overrideTargets == null ? targets : overrideTargets, properties
                .length() == 0 ? null : properties.toString());
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends IvyBuilderTypeDescriptor {

        @Override
        public String getDisplayName() {
            return "NAnt Builder";
        }

        public NantInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(NantBuilder.DescriptorImpl.class).getInstallations();
        }

    }
}
