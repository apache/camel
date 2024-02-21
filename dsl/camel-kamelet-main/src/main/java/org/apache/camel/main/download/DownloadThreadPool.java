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
package org.apache.camel.main.download;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;

/**
 * A basic thread pool that run each download task in their own thread, and LOG download activity during download.
 */
class DownloadThreadPool extends ServiceSupport implements CamelContextAware {

    private final MavenDependencyDownloader downloader;
    private CamelContext camelContext;
    private volatile ExecutorService executorService;
    private boolean verbose;

    public DownloadThreadPool(MavenDependencyDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while downloading: {}", e.getMessage(), e);
                return;
            } catch (Exception e) {
                log.error("Error downloading: {} due to: {}", gav, e.getMessage(), e);
                return;
            }
            if (!done) {
                log.info("Downloading: {} (elapsed: {})", gav, TimeUtils.printDuration(watch.taken()));
            }
        }

        MavenGav a = MavenGav.parseGav(gav);
        DownloadRecord downloadRecord = downloader.getDownloadState(a.getGroupId(), a.getArtifactId(), a.getVersion());
        if (downloadRecord != null) {
            long taken = watch.taken();
            String url = downloadRecord.repoUrl();
            String id = downloadRecord.repoId();
            String msg = "Downloaded: " + gav + " (took: "
                         + TimeUtils.printDuration(taken, true) + ") from: " + id + "@" + url;
            log.info(msg);
        } else {
            long taken = watch.taken();
            String msg = "Resolved: " + gav + " (took: "
                         + TimeUtils.printDuration(taken, true) + ")";
            if (verbose || taken > 2000) {
                // slow resolving then log
                log.info(msg);
            } else {
                log.debug(msg);
            }
        }
    }

    @Override
    protected void doBuild() throws Exception {
        if (camelContext != null) {
            executorService = camelContext.getExecutorServiceManager().newCachedThreadPool(this, "MavenDownload");
        } else {
            executorService = Executors.newCachedThreadPool();
        }
        downloader.setVerbose(verbose);
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executorService != null && camelContext != null) {
            camelContext.getExecutorServiceManager().shutdown(executorService);
        } else if (executorService != null) {
            executorService.shutdown();
        }
    }
}
