package hudson.ivy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class IvyModuleSetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void should_support_jcasc_from_yaml() throws Exception {
        IvyModuleSet.DescriptorImpl globalConfig = j.jenkins.getDescriptorByType(IvyModuleSet.DescriptorImpl.class);

        String yamlUrl = getClass()
                .getResource(getClass().getSimpleName() + "/configuration-as-code.yml")
                .toString();
        ConfigurationAsCode.get().configure(yamlUrl);

        assertThat(globalConfig.getGlobalAntOpts(), equalTo("-Dproperty1=value1\n-Dproperty2=value2"));
    }

    @Test
    public void should_support_jcasc_to_yaml() throws Exception {
        IvyModuleSet.DescriptorImpl globalConfig = j.jenkins.getDescriptorByType(IvyModuleSet.DescriptorImpl.class);

        globalConfig.setGlobalAntOpts("-Dproperty1=value1\n-Dproperty2=value2");

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
