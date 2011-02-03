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

import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.remoting.Callable;
import hudson.remoting.DelegatingCallable;
import hudson.remoting.Future;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.tools.ant.BuildEvent;

/**
 * {@link Callable} that invokes Ant CLI (in process) and drives a build.
 *
 * <p>
 * As a callable, this function returns the build result.
 *
 * <p>
 * This class defines a series of event callbacks, which are invoked during the build.
 * This allows subclass to monitor the progress of a build.
 *
 * @author Timothy Bingaman
 */
public abstract class IvyBuilder implements DelegatingCallable<Result,IOException> {
    /**
     * Goals to be executed in this Ant execution.
     */
    private final List<String> goals;
    /**
     * Hudson-defined system properties. These will be made available to Ant,
     * and accessible as if they are specified as -Dkey=value
     */
    private final Map<String,String> systemProps;
    /**
     * Where error messages and so on are sent.
     */
    protected final BuildListener listener;

    /**
     * Flag needs to be set at the constructor, so that this reflects
     * the setting at master.
     */
//    private final boolean profile = AntProcessFactory.profile;

    /**
     * Record all asynchronous executions as they are scheduled,
     * to make sure they are all completed before we finish.
     */
    protected transient /*final*/ List<Future<?>> futures;

    protected IvyBuilder(BuildListener listener, List<String> goals, Map<String, String> systemProps) {
        this.listener = listener;
        this.goals = goals;
        this.systemProps = systemProps;
    }

    /**
     * Called before the whole build.
     */
    abstract void preBuild(BuildEvent event) throws IOException, InterruptedException;

    /**
     * Called after the build has completed fully.
     */
    abstract void postBuild(BuildEvent event) throws IOException, InterruptedException;

    /**
     * Called when a build enter another module.
     */
    abstract void preModule(BuildEvent event) throws InterruptedException, IOException;

    /**
     * Called when a build leaves a module.
     */
    abstract void postModule(BuildEvent event) throws InterruptedException, IOException;

    /**
     * This code is executed inside the Ant jail process.
     */
    public Result call() throws IOException {
        try {
            futures = new ArrayList<Future<?>>();
            Adapter a = new Adapter(this);
//            PluginManagerInterceptor.setListener(a);
//            LifecycleExecutorInterceptor.setListener(a);

//            markAsSuccess = false;

            System.getProperties().putAll(systemProps);

            listener.getLogger().println(formatArgs(goals));
//            int r = Main.launch(goals.toArray(new String[goals.size()]));

            // now check the completion status of async ops
            boolean messageReported = false;
            long startTime = System.nanoTime();
            for (Future<?> f : futures) {
                try {
                    if(!f.isDone() && !messageReported) {
                        messageReported = true;
                        listener.getLogger().println(Messages.IvyBuilder_Waiting());
                    }
                    f.get();
                } catch (InterruptedException e) {
                    // attempt to cancel all asynchronous tasks
                    for (Future<?> g : futures)
                        g.cancel(true);
                    listener.getLogger().println(Messages.IvyBuilder_Aborted());
                    return Result.ABORTED;
                } catch (ExecutionException e) {
//                    e.printStackTrace(listener.error(Messages.IvyBuilder_AsyncFailed()));
                }
            }
            a.overheadTime += System.nanoTime()-startTime;
            futures.clear();

//            if(profile) {
//                NumberFormat n = NumberFormat.getInstance();
//                PrintStream logger = listener.getLogger();
//                logger.println("Total overhead was "+format(n,a.overheadTime)+"ms");
//                Channel ch = Channel.current();
//                logger.println("Class loading "   +format(n,ch.classLoadingTime.get())   +"ms, "+ch.classLoadingCount+" classes");
//                logger.println("Resource loading "+format(n,ch.resourceLoadingTime.get())+"ms, "+ch.resourceLoadingCount+" times");
//            }

//            if(r==0)    return Result.SUCCESS;

//            if(markAsSuccess) {
//                return Result.SUCCESS;
//            }

            listener.getLogger().println(Messages.IvyBuilder_Failed());
            return Result.FAILURE;
        } finally {
//            PluginManagerInterceptor.setListener(null);
//            LifecycleExecutorInterceptor.setListener(null);
        }
    }

    private String formatArgs(List<String> args) {
        StringBuilder buf = new StringBuilder("Executing Ant: ");
        for (String arg : args)
            buf.append(' ').append(arg);
        return buf.toString();
    }

    private String format(NumberFormat n, long nanoTime) {
        return n.format(nanoTime/1000000);
    }

    // since reporters might be from plugins, use the uberjar to resolve them.
    public ClassLoader getClassLoader() {
        return Hudson.getInstance().getPluginManager().uberClassLoader;
    }

    /**
     * Receives various events and converts them to Ant {@link BuildEvent}s.
     */
    private static final class Adapter implements org.apache.tools.ant.BuildListener {

        private final IvyBuilder listener;

        /**
         * Number of total nanoseconds {@link IvyBuilder} spent.
         */
        long overheadTime;

        public Adapter(IvyBuilder listener) {
            this.listener = listener;
        }

        public void buildFinished(BuildEvent event) {
            long startTime = System.nanoTime();
            try {
                listener.postBuild(event);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            overheadTime += System.nanoTime()-startTime;
        }

        public void buildStarted(BuildEvent event) {
            long startTime = System.nanoTime();
            try {
                listener.preBuild(event);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            overheadTime += System.nanoTime()-startTime;
        }

        public void messageLogged(BuildEvent event) {
            // TODO Auto-generated method stub

        }

        public void targetFinished(BuildEvent event) {
            // TODO Auto-generated method stub

        }

        public void targetStarted(BuildEvent event) {
            // TODO Auto-generated method stub

        }

        public void taskFinished(BuildEvent event) {
            // TODO Auto-generated method stub

        }

        public void taskStarted(BuildEvent event) {
            // TODO Auto-generated method stub

        }
    }

    private static final long serialVersionUID = 1L;
}
