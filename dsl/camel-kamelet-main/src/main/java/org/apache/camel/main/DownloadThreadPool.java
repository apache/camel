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
package org.apache.camel.main;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;

/**
 * A basic thread pool that run each download task in their own thread, and LOG download activity during download.
 */
class DownloadThreadPool {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public void download(Logger log, Runnable task, String gav) {
        Future<?> future = executorService.submit(task);
        awaitCompletion(log, future, gav);
    }

    void awaitCompletion(Logger log, Future<?> future, String gav) {
        StopWatch watch = new StopWatch();
        boolean done = false;
        while (!done) {
            try {
                future.get(5000, TimeUnit.MILLISECONDS);
                done = true;
            } catch (TimeoutException e) {
                // not done
            } catch (Exception e) {
                log.error("Error downloading: " + gav + " due: " + e.getMessage());
                return;
            }
            if (!done) {
                log.info("Downloading: {} (elapsed: {})", gav, TimeUtils.printDuration(watch.taken()));
            }
        }

        // only report at INFO if downloading took > 1s because loading from cache is faster
        // and then it is not downloaded over the internet
        long taken = watch.taken();
        String msg = "Downloaded:  " + gav + " (took: "
                     + TimeUtils.printDuration(taken) + ")";
        if (taken < 1000) {
            log.debug(msg);
        } else {
            log.info(msg);
        }
    }

}
