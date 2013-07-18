/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Timothy Bingaman
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

import hudson.ivy.AbstractIvyBuild.ParameterizedUpstreamCause;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.ItemGroup;
import hudson.model.ParametersAction;
import hudson.triggers.Trigger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common part between {@link IvyModule} and {@link IvyModuleSet}.
 *
 * @author Timothy Bingaman
 */
public abstract class AbstractIvyProject<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends AbstractProject<P,R> {
    protected AbstractIvyProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        // if we just pick up the project actions from the last build,
        // and if the last build failed very early, then the reports that
        // kick in later (like test results) won't be displayed.
        // so pick up last successful build, too.
        Set<Class> added = new HashSet<Class>();
        addTransientActionsFromBuild(getLastBuild(), r, added);
        addTransientActionsFromBuild(getLastSuccessfulBuild(), r, added);

        for (Trigger<?> trigger : triggers)
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * @param collection
     *      Add the transient actions to this collection.
     */
    protected abstract void addTransientActionsFromBuild(R lastBuild, List<Action> collection, Set<Class> added);

    public abstract boolean isUseUpstreamParameters();

    @Override
    public boolean scheduleBuild(Cause c) {
        if(c instanceof ParameterizedUpstreamCause) {

            ParameterizedUpstreamCause upc = (ParameterizedUpstreamCause) c;

            if(isUseUpstreamParameters()) {
                List<ParametersAction> upStreamParams = upc.getUpStreamParameters();

                return scheduleBuild(getQuietPeriod(),c,upStreamParams.toArray(new ParametersAction[upStreamParams.size()]));
            }
        }

        return super.scheduleBuild(c);
    }
}
