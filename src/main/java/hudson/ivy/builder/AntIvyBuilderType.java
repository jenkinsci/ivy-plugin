package hudson.ivy.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.ivy.IvyModuleSet;
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
     * Optional build script path relative to the workspace. Used for the Ant
     * '-f' option.
     */
    private String buildFile;
    /**
     * Optional properties to be passed to Ant. Follows {@link Properties}
     * syntax.
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

    public String getAntName() {
        return antName;
    }

    public String getAntOpts() {
        return antOpts;
    }

    /**
     * Possibly null, whitespace-separated (including TAB, NL, etc) VM options
     * to be used to launch Ant process.
     *
     * If antOpts is null or empty, we'll return the globally-defined ANT_OPTS.
     */
    public String getFormattedAntOptsWithFallback() {
        if ((antOpts!=null) && (antOpts.trim().length()>0)) { 
            return antOpts.replaceAll("[\t\r\n]+"," ");
        }
        else {
            String globalOpts = IvyModuleSet.DESCRIPTOR.getGlobalAntOpts();
            if (globalOpts!=null) {
                return globalOpts.replaceAll("[\t\r\n]+"," ");
            }
            else {
                return globalOpts;
            }
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
        String opts = getFormattedAntOptsWithFallback();
        if (opts != null)
            envs.put("ANT_OPTS", opts);
        return envs;
    }

    @Override
    public Builder getBuilder(Properties additionalProperties, String overrideTargets) {
        StringBuilder properties = new StringBuilder();
        
        if (antProperties != null)
            properties.append(antProperties);

        if (additionalProperties != null) {
            for (String key : additionalProperties.stringPropertyNames()) {
                properties.append("\n");
                properties.append(key).append("=").append(additionalProperties.getProperty(key));
            }
        }
        return new Ant(overrideTargets == null ? targets : overrideTargets, antName, getFormattedAntOptsWithFallback(), buildFile, properties
                .length() == 0 ? null : properties.toString());
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
}
