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
package org.apache.camel.util.concurrent;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A synchronous {@link java.util.concurrent.ExecutorService} which always invokes
 * the task in the caller thread (just a thread pool facade).
 * <p/>
 * There is no task queue, and no thread pool. The task will thus always be executed
 * by the caller thread in a fully synchronous method invocation.
 * <p/>
 * This implementation is very simple and does not support waiting for tasks to complete during shutdown.
 *
 * @version
 */
public class SynchronousExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;

    public void shutdown() {
        shutdown = true;
    }

    public List<Runnable> shutdownNow() {
        // not implemented
        return null;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public boolean isTerminated() {
        return shutdown;
    }

    public boolean awaitTermination(long time, TimeUnit unit) throws InterruptedException {
        // not implemented
        return true;
    }

    public void execute(Runnable runnable) {
        // run the task synchronously
        runnable.run();
    }

}
