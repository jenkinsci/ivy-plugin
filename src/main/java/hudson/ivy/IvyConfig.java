package hudson.ivy;

import hudson.Extension;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.xml.XmlConfig;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class IvyConfig extends XmlConfig {

    public IvyConfig(String id, String name, String comment, String content) {
        super(id, name, comment, content);
    }

    @Extension
    public static final XmlConfigProvider provider = new XmlConfigProvider() {
        @Override
        public String getDisplayName() {
            return "Ivy configuration";
        }

        @Override
        public Config newConfig() {
            String id = getProviderId() + System.currentTimeMillis();
            return new Config(id, "IvyConfig", "", "<ivysettings>\n</ivysettings>");
        }
    };
}
