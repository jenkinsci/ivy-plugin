package hudson.ivy;

import hudson.tasks.BuildWrapper;


/**
 * A custom wrapper providing an extended {@link Environment} that can be used to customize the execution of Ant.
 * Additional Ant opts and command line arguments/targets that will be prepended to the build specified values.
 * <p/>
 * <p/>
 * Sample values may be:
 * <pre>
 * getAdditionalArgs=-lib /my-custom-tasks-dir -listener com.acme.MyBuildListener
 * getAdditionalOpts=-javaagent:path-to-agent.jar -DmyProp=prop
 * </pre>
 *
 * @author Tomer Cohen
 */
public class AntIvyBuildWrapper extends BuildWrapper {

    public abstract class AntIvyBuilderEnvironment extends BuildWrapper.Environment {
        public String getAdditionalArgs() {
            // Empty by default
            return "";
        }

        public String getAdditionalOpts() {
            // Empty by default
            return "";
        }
    }
}
