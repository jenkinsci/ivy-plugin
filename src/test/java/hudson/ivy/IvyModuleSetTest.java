package hudson.ivy;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class IvyModuleSetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void should_support_jcasc_from_yaml() throws Exception {
        IvyModuleSet.DescriptorImpl globalConfig = j.jenkins.getDescriptorByType(IvyModuleSet.DescriptorImpl.class);

        String yamlUrl = getClass().getResource(getClass().getSimpleName() + "/configuration-as-code.yml").toString();
        ConfigurationAsCode.get().configure(yamlUrl);

        assertThat(globalConfig.getGlobalAntOpts(), equalTo("-Dproperty1=value1\n-Dproperty2=value2"));
    }
}
