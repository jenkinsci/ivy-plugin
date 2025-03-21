package hudson.ivy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class IvyBuildTriggerTest {

    @Test
    @ConfiguredWithCode("IvyBuildTriggerTest/configuration-as-code.yml")
    void should_support_jcasc_from_yaml(JenkinsConfiguredWithCodeRule j) {
        IvyBuildTrigger.DescriptorImpl globalConfig =
                j.jenkins.getDescriptorByType(IvyBuildTrigger.DescriptorImpl.class);

        assertThat(globalConfig.getConfigurations(), arrayWithSize(2));
        assertThat(globalConfig.getConfigurations()[0].getName(), equalTo("setting1"));
        assertThat(globalConfig.getConfigurations()[0].getIvyConfPath(), equalTo("dir/file1.xml"));
        assertThat(globalConfig.getConfigurations()[1].getName(), equalTo("setting2"));
        assertThat(globalConfig.getConfigurations()[1].getIvyConfPath(), equalTo("dir/file2.xml"));
        assertThat(globalConfig.isExtendedVersionMatching(), equalTo(true));
    }

    @Test
    @ConfiguredWithCode("IvyBuildTriggerTest/configuration-as-code.yml")
    void should_support_jcasc_to_yaml(JenkinsConfiguredWithCodeRule j) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ConfigurationAsCode.get().export(outputStream);
        String exportedYaml = outputStream.toString(StandardCharsets.UTF_8);

        InputStream yamlStream =
                getClass().getResourceAsStream(getClass().getSimpleName() + "/configuration-as-code.yml");
        String expectedYaml = IOUtils.toString(yamlStream, StandardCharsets.UTF_8)
                .replaceAll("\r\n?", "\n")
                .replace("unclassified:\n", "");

        assertThat(exportedYaml, containsString(expectedYaml));
    }
}
