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

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Hudson;
import hudson.tasks.BuildStep;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.tools.ant.BuildEvent;

/**
 * Listens to the build execution of {@link IvyBuild},
 * and normally records some information and exposes thoses
 * in {@link IvyBuild} later.
 *
 * <p>
 * {@link IvyReporter} is first instanciated on the master.
 * Then during the build, it is serialized and sent over into
 * the Ant process by serialization. Reporters will then receive
 * event callbacks as Ant build events. Those event callbacks
 * are the ones that take {@link IvyBuildProxy}.
 *
 * <p>
 * Once the Ant build completes normally or abnormally, the reporters
 * will be sent back to the master by serialization again, then
 * have its {@link #end(IvyBuild, Launcher, BuildListener)} method invoked.
 * This is a good opportunity to perform the post-build action.
 *
 * <p>
 * This is the {@link IvyBuild} equivalent of {@link BuildStep}. Instances
 * of {@link IvyReporter}s are persisted with {@link IvyModule}/{@link IvyModuleSet},
 * possibly with configuration specific to that job.
 *
 *
 * <h2>Callback Firing Sequence</h2>
 * <p>
 * The callback methods are invoked in the following order:
 *
 * <pre>
 * SEQUENCE := preBuild MODULE* postBuild end
 * MODULE   := enterModule leaveModule
 * </pre>
 *
 * <p>
 * When an error happens, the call sequence could be terminated at any point
 * and no further callback methods may be invoked.
 *
 *
 * <h2>Action</h2>
 * <p>
 * {@link IvyReporter} can {@link IvyBuild#addAction(Action) contribute}
 * {@link Action} to {@link IvyBuild} so that the report can be displayed
 * in the web UI.
 *
 * <p>
 * Such action can also implement {@link AggregatableAction} if it further
 * wishes to contribute a separate action to {@link IvyModuleSetBuild}.
 * This mechanism is usually used to provide aggregated report for all the
 * module builds.
 *
 * @author Kohsuke Kawaguchi
 * @see IvyReporters
 */
public abstract class IvyReporter implements Describable<IvyReporter>, ExtensionPoint, Serializable {
    /**
     * Called before the actual ant execution begins.
     *
     * @param moduleDescriptor
     *      Represents the Ivy module to be executed.
     * @return
     *      true if the build can continue, false if there was an error
     *      and the build needs to be aborted.
     * @throws InterruptedException
     *      If the build is interrupted by the user (in an attempt to abort the build.)
     *      Normally the {@link IvyReporter} implementations may simply forward the exception
     *      it got from its lower-level functions.
     * @throws IOException
     *      If the implementation wants to abort the processing when an {@link IOException}
     *      happens, it can simply propagate the exception to the caller. This will cause
     *      the build to fail, with the default error message.
     *      Implementations are encouraged to catch {@link IOException} on its own to
     *      provide a better error message, if it can do so, so that users have better
     *      understanding on why it failed.
     */
    public boolean preBuild(IvyBuildProxy build, BuildEvent event, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called when the build enters a next {@link IvyProject}.
     *
     * <p>
     * When the current build is a multi-module reactor build, every time the build
     * moves on to the next module, this method will be invoked.
     *
     * @return
     *      See {@link #preBuild}
     * @throws InterruptedException
     *      See {@link #preBuild}
     * @throws IOException
     *      See {@link #preBuild}
     */
    public boolean enterModule(IvyBuildProxy build, BuildEvent event, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called when the build leaves the current {@link IvyProject}.
     *
     * @see #enterModule
     */
    public boolean leaveModule(IvyBuildProxy build, BuildEvent event, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after a build of one Ivy module is completed.
     *
     * <p>
     * Note that at this point the build result is still not determined.
     *
     * @return
     *      See {@link #preBuild}
     * @throws InterruptedException
     *      See {@link #preBuild}
     * @throws IOException
     *      See {@link #preBuild}
     */
    public boolean postBuild(IvyBuildProxy build, BuildEvent event, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Called after the Ant/Ivy execution finished and the result is determined.
     *
     * <p>
     * This method fires after {@link #postBuild(IvyBuildProxy, ModuleDescriptor, BuildListener)}.
     * Works like {@link Publisher#perform(Build, Launcher, BuildListener)}.
     */
    public boolean end(IvyBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    /**
     * Equivalent of {@link BuildStep#getProjectAction(AbstractProject)}
     * for {@link IvyReporter}.
     *
     * <p>
     * Registers a transient action to {@link IvyModule} when it's rendered.
     * This is useful if you'd like to display an action at the module level.
     *
     * <p>
     * Since this contributes a transient action, the returned {@link Action}
     * will not be serialized.
     *
     * <p>
     * For this method to be invoked, your {@link IvyReporter} has to invoke
     * {@link IvyBuildProxy#registerAsProjectAction(IvyReporter)} during the build.
     *
     * @return
     *      null not to contribute an action, which is the default.
     * @deprecated as of 1.21
     *      Use {@link #getProjectActions(IvyModule)} instead.
     */
    public Action getProjectAction(IvyModule module) {
        return null;
    }

    /**
     * Equivalent of {@link BuildStep#getProjectActions(AbstractProject)}
     * for {@link IvyReporter}.
     *
     * <p>
     * Registers a transient action to {@link IvyModule} when it's rendered.
     * This is useful if you'd like to display an action at the module level.
     *
     * <p>
     * Since this contributes a transient action, the returned {@link Action}
     * will not be serialized.
     *
     * <p>
     * For this method to be invoked, your {@link IvyReporter} has to invoke
     * {@link IvyBuildProxy#registerAsProjectAction(IvyReporter)} during the build.
     *
     * @return
     *      can be empty but never null.
     * @since 1.21
     */
    public Collection<? extends Action> getProjectActions(IvyModule module) {
        // delegate to getProjectAction (singular) for backward compatible behavior
        Action a = getProjectAction(module);
        if (a==null)    return Collections.emptyList();
        return Collections.singletonList(a);
    }

    /**
     * Works like {@link #getProjectAction(IvyModule)} but
     * works at {@link IvyModuleSet} level.
     *
     * <p>
     * For this method to be invoked, your {@link IvyReporter} has to invoke
     * {@link IvyBuildProxy#registerAsAggregatedProjectAction(IvyReporter)} during the build.
     *
     * @return
     *      null not to contribute an action, which is the default.
     */
    public Action getAggregatedProjectAction(IvyModuleSet project) {
        return null;
    }

    public IvyReporterDescriptor getDescriptor() {
        return (IvyReporterDescriptor)Hudson.getInstance().getDescriptorOrDie(getClass());
    }
}
