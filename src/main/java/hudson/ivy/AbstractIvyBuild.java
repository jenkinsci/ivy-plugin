/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc., Victor Glushenkov, Timothy Bingaman
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

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.DependencyGraph;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildTrigger;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;

public abstract class AbstractIvyBuild<P extends AbstractIvyProject<P, B>, B extends AbstractIvyBuild<P, B>>
        extends AbstractBuild<P, B> {

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

    /**
     * Schedules all the downstream builds.
     * Returns immediately if build result doesn't meet the required level
     * (as specified by {@link BuildTrigger}, or {@link Result#SUCCESS} if none).
     *
     * @param listener
     *      Where the progress reports go.
     */
    protected final void scheduleDownstreamBuilds(BuildListener listener) {
        BuildTrigger bt = getParent().getPublishersList().get(BuildTrigger.class);
        if (getResult().isWorseThan(bt != null ? bt.getThreshold() : Result.SUCCESS)) {
            return;
        }

        // trigger dependency builds
        for (AbstractProject<?, ?> down : getParent().getDownstreamProjects()) {
            if (debug) {
                listener.getLogger().println("Considering whether to trigger " + down + " or not");
            }

            // if the downstream module depends on multiple modules,
            // only trigger them when all the upstream dependencies are updated.
            boolean trigger = true;

            if (down.isInQueue()) {
                if (debug) {
                    listener.getLogger().println(" -> No, because downstream is already in queue");
                }
                trigger = false;
            }
            // Check to see if any of its upstream dependencies are already building or in queue.
            else if (areUpstreamsBuilding(down, getParent())) {
                if (debug) {
                    listener.getLogger()
                            .println(" -> No, because downstream has dependencies already building or in queue");
                }
                trigger = false;
            }
            // Check to see if any of its upstream dependencies are in this list of downstream projects.
            else if (inDownstreamProjects(down)) {
                if (debug) {
                    listener.getLogger()
                            .println(" -> No, because downstream has dependencies in the downstream projects list");
                }
                trigger = false;
            } else {
                AbstractBuild<?, ?> dlb = down.getLastBuild(); // can be null.
                for (AbstractIvyProject up : Util.filter(down.getUpstreamProjects(), AbstractIvyProject.class)) {
                    Run ulb;
                    if (up == getParent()) {
                        // the current build itself is not registered as lastSuccessfulBuild
                        // at this point, so we have to take that into account. ugly.
                        if (getResult() == null || !getResult().isWorseThan(Result.UNSTABLE)) {
                            ulb = this;
                        } else {
                            ulb = up.getLastSuccessfulBuild();
                        }
                    } else {
                        ulb = up.getLastSuccessfulBuild();
                    }
                    if (ulb == null) {
                        // if no usable build is available from the upstream,
                        // then we have to wait at least until this build is ready
                        if (debug) {
                            listener.getLogger()
                                    .println(" -> No, because another upstream " + up + " for " + down
                                            + " has no successful build");
                        }
                        trigger = false;
                        break;
                    }

                    // if no record of the relationship in the last build
                    // is available, we'll just have to assume that the condition
                    // for the new build is met, or else no build will be fired forever.
                    if (dlb == null) {
                        continue;
                    }
                    int n = dlb.getUpstreamRelationship(up);
                    if (n == -1) {
                        continue;
                    }

                    assert ulb.getNumber() >= n;
                }
            }

            if (trigger) {
                listener.getLogger().println(Messages.IvyBuild_Triggering(down.getName()));
                down.scheduleBuild(
                        new ParameterizedUpstreamCause((Run<?, ?>) this, this.getActions(ParametersAction.class)));
            }
        }
    }

    public static class ParameterizedUpstreamCause extends UpstreamCause {
        private final List<ParametersAction> upStreamParameters;

        public ParameterizedUpstreamCause(Run<?, ?> arg0, List<ParametersAction> upStreamParams) {
            super(arg0);
            upStreamParameters = upStreamParams;
        }

        public List<ParametersAction> getUpStreamParameters() {
            return upStreamParameters;
        }
    }

    private boolean inDownstreamProjects(AbstractProject downstreamProject) {
        DependencyGraph graph = Jenkins.get().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);

        for (AbstractProject tup : tups) {
            for (AbstractProject<?, ?> dp : getParent().getDownstreamProjects()) {
                if (dp != getParent() && dp != downstreamProject && dp == tup) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether any of the upstream project are either
     * building or in the queue.
     *
     * This means eventually there will be an automatic triggering of
     * the given project (provided that all builds went smoothly.)
     *
     * @param downstreamProject
     *      The AbstractProject we want to build.
     * @param excludeProject
     *      An AbstractProject to exclude - if we see this in the transitive
     *      dependencies, we're not going to bother checking to see if it's
     *      building. For example, pass the current parent project to be sure
     *      that it will be ignored when looking for building dependencies.
     * @return
     *      True if any upstream projects are building or in queue, false otherwise.
     */
    private boolean areUpstreamsBuilding(AbstractProject downstreamProject, AbstractProject excludeProject) {
        DependencyGraph graph = Jenkins.get().getDependencyGraph();
        Set<AbstractProject> tups = graph.getTransitiveUpstream(downstreamProject);
        for (AbstractProject tup : tups) {
            if (tup != excludeProject && (tup.isBuilding() || tup.isInQueue())) {
                return true;
            }
        }
        return false;
    }
}
