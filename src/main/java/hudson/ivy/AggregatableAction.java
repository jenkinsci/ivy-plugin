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

import hudson.model.Action;
import java.util.List;
import java.util.Map;

/**
 * Indicates that this {@link Action} for {@link IvyBuild} contributes
 * an "aggregated" action to {@link IvyBuild#getModuleSetBuild()
 * its governing IvyModuleSetBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.99
 * @see IvyReporter
 */
public interface AggregatableAction extends Action {
    /**
     * Creates {@link Action} to be contributed to {@link IvyModuleSetBuild}.
     *
     * @param build
     *      {@link IvyModuleSetBuild} for which the aggregated report is
     *      created.
     * @param moduleBuilds
     *      The result of {@link IvyModuleSetBuild#getModuleBuilds()} provided
     *      for convenience and efficiency.
     * @return
     *      null if the reporter provides no such action.
     */
    IvyAggregatedReport createAggregatedAction(IvyModuleSetBuild build, Map<IvyModule, List<IvyBuild>> moduleBuilds);
}
