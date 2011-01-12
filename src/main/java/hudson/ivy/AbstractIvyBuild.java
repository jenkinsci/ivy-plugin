/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Victor Glushenkov
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

import hudson.model.AbstractBuild;
import hudson.model.Cause.UpstreamCause;
import hudson.model.ParametersAction;
import hudson.model.Run;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public abstract class AbstractIvyBuild<P extends AbstractIvyProject<P,B>,B extends AbstractIvyBuild<P,B>> extends AbstractBuild<P, B>  {
    /**
     * Extra verbose debug switch.
     */
    public static boolean debug = false;
    
    protected AbstractIvyBuild(P job) throws IOException {
        super(job);
    }
    
    public AbstractIvyBuild(P job, Calendar timestamp) {
        super(job, timestamp);
    }
    
    public AbstractIvyBuild(P project, File buildDir) throws IOException {
        super(project, buildDir);
    }
    
    public static class ParameterizedUpstreamCause extends UpstreamCause
    {
        private final List<ParametersAction> upStreamParameters;
        
        public ParameterizedUpstreamCause(Run<?, ?> arg0, List<ParametersAction> upStreamParams) {
            super(arg0);
            upStreamParameters = upStreamParams;
        }
        
        public List<ParametersAction> getUpStreamParameters()
        {
            return upStreamParameters;
        }
    }
}
