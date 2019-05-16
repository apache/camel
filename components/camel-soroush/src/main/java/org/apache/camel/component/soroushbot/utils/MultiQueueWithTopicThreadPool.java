/*
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
package org.apache.camel.component.soroushbot.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a simple thread pool that send each job to a thread based on the jobs topic,
 */
public class MultiQueueWithTopicThreadPool {
    private static Logger log = LoggerFactory.getLogger(MultiQueueWithTopicThreadPool.class);
    private boolean shutdown; //default is false
    private int poolSize;
    private List<PoolWorker> workers = new ArrayList<>();

    public MultiQueueWithTopicThreadPool(int poolSize, int capacity, String namePrefix) {
        if (log.isDebugEnabled()) {
            log.debug("creating MultiQueueWithTopicThreadPool with size " + poolSize + " and capacity of each queue is set to " + capacity);
        }
        this.poolSize = poolSize;
        //create a pool of thread and start them
        for (int i = 0; i < poolSize; i++) {
            PoolWorker e = new PoolWorker(capacity);
            workers.add(e);
            e.start();
            e.setName(namePrefix + " #" + i);
        }
    }

    /**
     * add the runnable into corresponding queue and it when it reach to the head of queue
     * the queue is decided based on {@code topic}. if topic is instance of Integer,
     * it uses (topic%poolSize) to determine corresponding queue otherwise it uses
     * (topic.hashCode()%poolsize) do determine corresponding queue.
     *
     * @param topic    tasks are organized between threads based on this parameter
     * @param runnable the task that should be executed
     * @throws IllegalStateException if the {@code runnable} cannot be added at this
     *                               time due to queue capacity restrictions
     */
    public void execute(Object topic, Runnable runnable) throws IllegalStateException {
        if (shutdown) {
            throw new RejectedExecutionException("pool has been shutdown");
        }
        int selectedQueue;
        if (topic instanceof Integer) {
            selectedQueue = ((Integer) topic) % poolSize;
        } else {
            selectedQueue = topic.hashCode() % poolSize;
        }
        PoolWorker poolWorker = workers.get(selectedQueue);
        synchronized (poolWorker) {
            poolWorker.enqueue(runnable);
        }
    }

    public void shutdown() {
        shutdown = true;
    }
}

/**
 * Each PoolWorker is a thread that when it is idle, it pick the head from its and
 * execute it.
 */
class PoolWorker extends Thread {
    private static Logger log = LoggerFactory.getLogger(PoolWorker.class);
    final LinkedBlockingQueue<Runnable> queue;

    public PoolWorker(int capacity) {
        // if capacity <=0 then the queue capacity should be {@link Integer#MAX_VALUE}
        if (capacity > 0) {
            queue = new LinkedBlockingQueue<>(capacity);
        } else {
            queue = new LinkedBlockingQueue<>();
        }
    }

    /**
     * ad new runnable to queue and notify corresponding thread to execute newly added
     * runnable if the thread is idle.
     *
     * @param r a runnable to execute by this threadPool
     */
    public void enqueue(Runnable r) {
        synchronized (queue) {
            queue.add(r);
            queue.notify();
        }
    }

    @Override
    public void run() {
        while (true) {
            Runnable task;
            synchronized (queue) {
                // while queue is empty wait for queue to become populated
                while (queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                        log.error("An interrupt occurred while queue is waiting: " + e.getMessage());
                        //interrupt current thread to prevent the interrupt being swallowed.
                        Thread.currentThread().interrupt();
                    }
                }
                //poll next task as we know it is exists in the queue
                task = queue.poll();
                //double check!
                if (task == null) {
                    continue;
                }
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                //catch RuntimeException that may thrown in the task
                log.error("Thread pool is interrupted due to an issue: " + e.getMessage());
            }

        }
    }
}