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

import hudson.FilePath;
import hudson.model.Result;
import hudson.remoting.Callable;
import hudson.remoting.DelegatingCallable;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;

/**
 * Remoting proxy interface for {@link IvyReporter}s to talk to {@link IvyBuild}
 * during the build.
 *
 * @author Timothy Bingaman
 */
public interface IvyBuildProxy {
    /**
     * Executes the given {@link BuildCallable} on the master, where one
     * has access to {@link IvyBuild} and all the other Hudson objects.
     *
     * <p>
     * The parameter, return value, and exception are all transfered by using
     * Java serialization.
     *
     * @return
     *      the value that {@link BuildCallable} returned.
     * @throws T
     *      if {@link BuildCallable} throws this exception.
     * @throws IOException
     *      if the remoting failed.
     * @throws InterruptedException
     *      if the remote execution is aborted.
     * @see #executeAsync(BuildCallable)
     */
    <V,T extends Throwable> V execute( BuildCallable<V,T> program ) throws T, IOException, InterruptedException;

    /**
     * Executes the given {@link BuildCallable} asynchronously on the master.
     * <p>
     * This method works like {@link #execute(BuildCallable)} except that
     * the method returns immediately and doesn't wait for the completion of the program.
     * <p>
     * The completions of asynchronous executions are accounted for before
     * the build completes. If they throw exceptions, they'll be reported
     * and the build will be marked as a failure. 
     */
    void executeAsync( BuildCallable<?,?> program ) throws IOException;

    /**
     * Root directory of the build.
     *
     * @see IvyBuild#getRootDir() 
     */
    FilePath getRootDir();

    /**
     * Root directory of the parent of this build.
     */
    FilePath getProjectRootDir();

    /**
     * Root directory of the owner {@link IvyModuleSet}
     */
    FilePath getModuleSetRootDir();

    /**
     * @see IvyBuild#getArtifactsDir()
     */
    FilePath getArtifactsDir();

    /**
     * @see IvyBuild#setResult(Result)
     */
    void setResult(Result result);

    /**
     * @see IvyBuild#getTimestamp()
     */
    Calendar getTimestamp();

    /**
     * # of milliseconds elapsed since {@link #getTimestamp()}.
     *
     * Where the clock skew is involved between the master and the Ivy JVM, comparing
     * current time on Ivy JVM with {@link #getTimestamp()} could be problematic,
     * but this value is more robust.
     */
    long getMilliSecsSinceBuildStart();

    /**
     * If true, artifacts will not actually be archived to master. Calls {@link IvyModuleSet#isArchivingDisabled()}.
     */
    boolean isArchivingDisabled();
    
    /**
     * Nominates that the reporter will contribute a project action
     * for this build by using {@link IvyReporter#getProjectAction(IvyModule)}.
     *
     * <p>
     * The specified {@link IvyReporter} object will be transfered to the master
     * and will become a persisted part of the {@link IvyBuild}. 
     */
    void registerAsProjectAction(IvyReporter reporter);

    /**
     * Nominates that the reporter will contribute a project action
     * for this build by using {@link IvyReporter#getAggregatedProjectAction(IvyModuleSet)}.
     *
     * <p>
     * The specified {@link IvyReporter} object will be transfered to the master
     * and will become a persisted part of the {@link IvyModuleSetBuild}.
     */
    void registerAsAggregatedProjectAction(IvyReporter  reporter);

    public interface BuildCallable<V,T extends Throwable> extends Serializable {
        /**
         * Performs computation and returns the result,
         * or throws some exception.
         *
         * @throws InterruptedException
         *      if the processing is interrupted in the middle. Exception will be
         *      propagated to the caller.
         * @throws IOException
         *      if the program simply wishes to propage the exception, it may throw
         *      {@link IOException}.
         */
        V call(IvyBuild build) throws T, IOException, InterruptedException;
    }

    /**
     * Filter for {@link IvyBuildProxy}.
     *
     * Meant to be useful as the base class for other filters.
     */
    /*package*/ static abstract class Filter<CORE extends IvyBuildProxy> implements IvyBuildProxy, Serializable {
        protected final CORE core;

        protected Filter(CORE core) {
            this.core = core;
        }

        public <V, T extends Throwable> V execute(BuildCallable<V, T> program) throws T, IOException, InterruptedException {
            return core.execute(program);
        }

        public void executeAsync(BuildCallable<?, ?> program) throws IOException {
            core.executeAsync(program);
        }

        public FilePath getRootDir() {
            return core.getRootDir();
        }

        public FilePath getProjectRootDir() {
            return core.getProjectRootDir();
        }

        public FilePath getModuleSetRootDir() {
            return core.getModuleSetRootDir();
        }

        public FilePath getArtifactsDir() {
            return core.getArtifactsDir();
        }

        public void setResult(Result result) {
            core.setResult(result);
        }

        public Calendar getTimestamp() {
            return core.getTimestamp();
        }

        public long getMilliSecsSinceBuildStart() {
            return core.getMilliSecsSinceBuildStart();
        }

        public boolean isArchivingDisabled() {
            return core.isArchivingDisabled();
        }
        
        public void registerAsProjectAction(IvyReporter reporter) {
            core.registerAsProjectAction(reporter);
        }

        public void registerAsAggregatedProjectAction(IvyReporter reporter) {
            core.registerAsAggregatedProjectAction(reporter);
        }

        private static final long serialVersionUID = 1L;

        /**
         * {@link Callable} for invoking {@link BuildCallable} asynchronously.
         */
        protected static final class AsyncInvoker implements DelegatingCallable<Object,Throwable> {
            private final IvyBuildProxy proxy;
            private final BuildCallable<?,?> program;

            public AsyncInvoker(IvyBuildProxy proxy, BuildCallable<?,?> program) {
                this.proxy = proxy;
                this.program = program;
            }

            public ClassLoader getClassLoader() {
                return program.getClass().getClassLoader();
            }

            public Object call() throws Throwable {
                // by the time this method is invoked on the master, proxy points to a real object
                proxy.execute(program);
                return null;    // ignore the result, as there's no point in sending it back
            }

            private static final long serialVersionUID = 1L;
        }
    }
}
