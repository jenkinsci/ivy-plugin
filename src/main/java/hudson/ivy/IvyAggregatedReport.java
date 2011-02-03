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

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.tasks.BuildStep;

import java.util.List;
import java.util.Map;

/**
 * {@link Action} to be associated with {@link IvyModuleSetBuild},
 * which usually displays some aspect of the aggregated results
 * of the module builds (such as aggregated test result, aggregated
 * coverage report, etc.)
 *
 * <p>
 * When a module build is completed, {@link IvyBuild#getModuleSetBuild()
 * its governing IvyModuleSetBuild} tries to create an instane of
 * {@link IvyAggregatedReport} from each kind of {@link IvyReporterDescriptor}
 * whose {@link IvyReporter}s are used on module builds.
 *
 * <p>
 * The obtained instance is then persisted with {@link IvyModuleSetBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.99
 * @see AggregatableAction
 */
public interface IvyAggregatedReport extends Action {
    /**
     * Called whenever a new module build is completed, to update the
     * aggregated report. When multiple builds complete simultaneously,
     * Hudson serializes the execution of this method, so this method
     * needs not be concurrency-safe.
     *
     * @param moduleBuilds
     *      Same as <tt>IvyModuleSet.getModuleBuilds()</tt> but provided for convenience and efficiency.
     * @param newBuild
     *      Newly completed build.
     */
    void update(Map<IvyModule,List<IvyBuild>> moduleBuilds, IvyBuild newBuild);

    /**
     * Returns the implementation class of {@link AggregatableAction} that
     * produces this {@link IvyAggregatedReport}. Hudson uses this method
     * to determine which {@link AggregatableAction} is aggregated to
     * which {@link IvyAggregatedReport}.
     */
    Class<? extends AggregatableAction> getIndividualActionType();

    /**
     * Equivalent of {@link BuildStep#getProjectAction(AbstractProject)}
     * for {@link IvyAggregatedReport}.
     */
    Action getProjectAction(IvyModuleSet moduleSet);
}
