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

package org.apache.camel.test.executor;

import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExecutor {
    Logger log = LoggerFactory.getLogger(this.getClass());
    ExecutorService executor;

    TestExecutor(int threadCount) {
        BlockingQueue<Runnable> queue = new SynchronousQueue<>();
        executor = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS, queue);
    }

    public void stop() {
        log.info("Stopping Excecutor Service");
        List<Runnable> runnables = executor.shutdownNow();
        log.info("{} Runnables were active", runnables == null ? 0 : runnables.size());
        for (Runnable runnable : runnables) {
            if (runnable instanceof TestRunnable) {
                log.info(((TestRunnable)runnable).status());
            } else {
                log.warn("Runnable is not instance of TestRunnable: {}", runnable.getClass().getName());
            }
        }
    }

    public void addRunnable(TestRunnable runnable) {
        executor.submit(runnable);
    }
}
