package hudson.ivy;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class IvyBuildTriggerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void should_support_jcasc_from_yaml() throws Exception {
        IvyBuildTrigger.DescriptorImpl globalConfig = j.jenkins.getDescriptorByType(IvyBuildTrigger.DescriptorImpl.class);

        String yamlUrl = getClass().getResource(getClass().getSimpleName() + "/configuration-as-code.yml").toString();
        ConfigurationAsCode.get().configure(yamlUrl);

        assertThat(globalConfig.getConfigurations()[0].getName(), equalTo("setting1"));
        assertThat(globalConfig.getConfigurations()[0].getIvyConfPath(), equalTo("dir/file1.xml"));
        assertThat(globalConfig.getConfigurations()[1].getName(), equalTo("setting2"));
        assertThat(globalConfig.getConfigurations()[1].getIvyConfPath(), equalTo("dir/file2.xml"));
        assertThat(globalConfig.getConfigurations(), arrayWithSize(2));
        assertThat(globalConfig.isExtendedVersionMatching(), equalTo(true));
    }
}
