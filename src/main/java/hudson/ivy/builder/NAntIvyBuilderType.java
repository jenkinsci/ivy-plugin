package hudson.ivy.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
    public Builder getBuilder(Properties additionalProperties) {
        StringBuilder properties = new StringBuilder();
        
        if (nantProperties != null)
            properties.append(nantProperties);

        if (additionalProperties != null) {
            for (String key : additionalProperties.stringPropertyNames()) {
                properties.append("\n");
                properties.append(key).append("=").append(additionalProperties.getProperty(key));
            }
        }
        return new NantBuilder(buildFile, nantName, targets, properties
                .length() == 0 ? null : properties.toString());
    }

    @Extension
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
