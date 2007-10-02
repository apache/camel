/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * A processor that forces async processing of the exchange using a thread pool.
 * 
 * @version $Revision$
 */
public class ThreadProcessor implements AsyncProcessor, Service {

    private ThreadPoolExecutor executor;
    private long stackSize;
    private ThreadGroup threadGroup;
    private int priority = Thread.NORM_PRIORITY;
    private boolean daemon = true;
    private String name = "Thread Processor";
    private BlockingQueue<Runnable> taskQueue;
    private long keepAliveTime;
    private int maxSize = 1;
    private int coreSize = 1;
    private final AtomicBoolean shutdown = new AtomicBoolean(true);;

    class ProcessCall implements Runnable {
        private final Exchange exchange;
        private final AsyncCallback callback;

        public ProcessCall(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        public void run() {
            if( shutdown.get() ) {
                exchange.setException(new RejectedExecutionException());
                callback.done(false);
            } else {
                callback.done(false);
            }
        }
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if( shutdown.get() ) {
            throw new IllegalStateException("ThreadProcessor is not running.");
        }
        ProcessCall call = new ProcessCall(exchange, callback);
        executor.execute(call);
        return false;
    }

    public void start() throws Exception {
        shutdown.set(false);
        getExecutor().setRejectedExecutionHandler(new RejectedExecutionHandler() {
            public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
                ProcessCall call = (ProcessCall)runnable;
                call.exchange.setException(new RejectedExecutionException());
                call.callback.done(false);
            }
        });
    }

    public void stop() throws Exception {
        shutdown.set(true);
        executor.shutdown();
        executor.awaitTermination(0, TimeUnit.SECONDS);
    }

    public long getStackSize() {
        return stackSize;
    }

    public void setStackSize(long stackSize) {
        this.stackSize = stackSize;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public void setThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public BlockingQueue<Runnable> getTaskQueue() {
        if (taskQueue == null) {
            taskQueue = new ArrayBlockingQueue<Runnable>(1000);
        }
        return taskQueue;
    }

    public void setTaskQueue(BlockingQueue<Runnable> taskQueue) {
        this.taskQueue = taskQueue;
    }

    public ThreadPoolExecutor getExecutor() {
        if (executor == null) {
            executor = new ThreadPoolExecutor(getCoreSize(), getMaxSize(), getKeepAliveTime(), TimeUnit.MILLISECONDS, getTaskQueue(), new ThreadFactory() {
                public Thread newThread(Runnable runnable) {
                    Thread thread;
                    if (getStackSize() > 0) {
                        thread = new Thread(getThreadGroup(), runnable, getName(), getStackSize());
                    } else {
                        thread = new Thread(getThreadGroup(), runnable, getName());
                    }
                    thread.setDaemon(isDaemon());
                    thread.setPriority(getPriority());
                    return thread;
                }
            });
        }
        return executor;
    }

    public void setExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

}
