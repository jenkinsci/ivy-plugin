package hudson.ivy;

import hudson.Extension;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.custom.CustomConfig;
import org.jenkinsci.plugins.configfiles.xml.XmlConfig;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class IvyConfig extends XmlConfig {

    public IvyConfig(String id, String name, String comment, String content) {
        super(id, name, comment, content);
    }

    @DataBoundConstructor
    public IvyConfig(String id, String name, String comment, String content, String providerId) {
        super(id, name, comment, content, providerId);
    }

    public IvyConfig(Config config) {
        super(config);
    }

    @Extension
    public static final XmlConfigProvider provider = new XmlConfigProvider() {
        @Override
        public String getDisplayName() {
            return "Ivy configuration";
        }

        @Override
        public IvyConfig newConfig() {
            String id = getProviderId() + System.currentTimeMillis();
            return new IvyConfig(id, "IvyConfig", "", "<ivysettings>\n</ivysettings>");
        }

        @Override
        public Config newConfig(String id) {
            return new IvyConfig(id, "IvyConfig", "", "<ivysettings>\n</ivysettings>", getProviderId());
        }

        @Override
        public <T extends Config> T convert(Config config) {
            return (T) new IvyConfig(config);
        }

    };
}
