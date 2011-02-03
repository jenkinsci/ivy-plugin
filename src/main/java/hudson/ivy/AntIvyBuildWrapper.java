/*
 * The MIT License
 * 
 * Copyright (c) 2010-2011, Tomer Cohen
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
