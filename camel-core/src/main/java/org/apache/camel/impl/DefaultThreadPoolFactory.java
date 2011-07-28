package org.apache.camel.impl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * Factory for thread pools that uses the JDK methods for handling thread pools
 */
public class DefaultThreadPoolFactory implements ThreadPoolFactory {
    
    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory factory) {
        return newThreadPool(profile.getPoolSize(), 
                             profile.getMaxPoolSize(), 
                             profile.getKeepAliveTime(),
                             profile.getTimeUnit(),
                             profile.getMaxQueueSize(), 
                             profile.getRejectedExecutionHandler(),
                             factory);
    }

    /**
     * Creates a new custom thread pool
     *
     * @param pattern                  pattern of the thread name
     * @param name                     ${name} in the pattern name
     * @param corePoolSize             the core pool size
     * @param maxPoolSize              the maximum pool size
     * @param keepAliveTime            keep alive time
     * @param timeUnit                 keep alive time unit
     * @param maxQueueSize             the maximum number of tasks in the queue, use <tt>Integer.MAX_VALUE</tt> or <tt>-1</tt> to indicate unbounded
     * @param rejectedExecutionHandler the handler for tasks which cannot be executed by the thread pool.
     *                                 If <tt>null</tt> is provided then {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} is used.
     * @param daemon                   whether the threads is daemon or not
     * @return the created pool
     * @throws IllegalArgumentException if parameters is not valid
     */
    private ExecutorService newThreadPool(int corePoolSize, 
                                          int maxPoolSize,
                                          long keepAliveTime, 
                                          TimeUnit timeUnit, 
                                          int maxQueueSize,
                                          RejectedExecutionHandler rejectedExecutionHandler, 
                                          final ThreadFactory factory) {

        // If we set the corePoolSize to be 0, the whole camel application will hang in JDK5
        // just add a check here to throw the IllegalArgumentException
        if (corePoolSize < 1) {
            throw new IllegalArgumentException("The corePoolSize can't be lower than 1");
        }
        
        // validate max >= core
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("MaxPoolSize must be >= corePoolSize, was " + maxPoolSize + " >= " + corePoolSize);
        }

        BlockingQueue<Runnable> queue;
        if (corePoolSize == 0 && maxQueueSize <= 0) {
            // use a synchronous queue
            queue = new SynchronousQueue<Runnable>();
            // and force 1 as pool size to be able to create the thread pool by the JDK
            corePoolSize = 1;
            maxPoolSize = 1;
        } else if (maxQueueSize <= 0) {
            // unbounded task queue
            queue = new LinkedBlockingQueue<Runnable>();
        } else {
            // bounded task queue
            queue = new LinkedBlockingQueue<Runnable>(maxQueueSize);
        }
        ThreadPoolExecutor answer = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, queue);
        answer.setThreadFactory(factory);
        if (rejectedExecutionHandler == null) {
            rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        }
        answer.setRejectedExecutionHandler(rejectedExecutionHandler);
        return answer;
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.impl.ThreadPoolFactory#newScheduledThreadPool(java.lang.Integer, java.util.concurrent.ThreadFactory)
     */
    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return Executors.newScheduledThreadPool(profile.getPoolSize(), threadFactory);
    }
}
