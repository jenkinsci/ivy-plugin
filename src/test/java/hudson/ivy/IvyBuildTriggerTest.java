package hudson.ivy;

import hudson.ivy.IvyBuildTrigger.IvyConfiguration;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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

    @Test
    public void should_support_jcasc_to_yaml() throws Exception {
        IvyBuildTrigger.DescriptorImpl globalConfig = j.jenkins.getDescriptorByType(IvyBuildTrigger.DescriptorImpl.class);

        IvyConfiguration config1 = new IvyConfiguration("setting1", "dir/file1.xml");
        IvyConfiguration config2 = new IvyConfiguration("setting2", "dir/file2.xml");
        globalConfig.setConfigurations(new IvyConfiguration[]{ config1, config2 });
        globalConfig.setExtendedVersionMatching(true);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(outputStream);
        String exportedYaml = outputStream.toString("UTF-8");

        InputStream yamlStream = getClass().getResourceAsStream(getClass().getSimpleName() + "/configuration-as-code.yml");
        String expectedYaml = IOUtils.toString(yamlStream, "UTF-8")
                .replaceAll("\r\n?", "\n")
                .replace("unclassified:\n", "");

        assertThat(exportedYaml, containsString(expectedYaml));
    }
}
