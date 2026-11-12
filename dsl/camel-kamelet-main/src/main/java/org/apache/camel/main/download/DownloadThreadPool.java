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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.maven.MavenResolutionException;
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
        download(log, task, gav, false);
    }

    /**
     * Downloads, and waits for the download to complete.
     *
     * @param required whether to throw a {@link DownloadException} when the download fails, instead of logging the
     *                 error
     */
    public void download(Logger log, Runnable task, String gav, boolean required) {
        download(log, task, List.of(gav), required);
    }

    /**
     * Downloads dependencies together, and waits for the download to complete.
     *
     * @param required whether to throw a {@link DownloadException} when the download fails, instead of logging the
     *                 error
     */
    public void download(Logger log, Runnable task, List<String> gavs, boolean required) {
        Future<?> future = executorService.submit(task);
        awaitCompletion(log, future, gavs, required);
    }

    void awaitCompletion(Logger log, Future<?> future, String gav) {
        awaitCompletion(log, future, List.of(gav), false);
    }

    void awaitCompletion(Logger log, Future<?> future, String gav, boolean required) {
        awaitCompletion(log, future, List.of(gav), required);
    }

    void awaitCompletion(Logger log, Future<?> future, List<String> gavs, boolean required) {
        String gav = String.join(", ", gavs);
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
                future.cancel(true);
                if (required) {
                    throw new DownloadException("Interrupted while downloading: " + gav, e);
                }
                log.error("Interrupted while downloading: {}", e.getMessage(), e);
                return;
            } catch (Exception e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                if (required) {
                    throw new DownloadException("Cannot download dependency: " + gav + " due to: " + reason(cause), cause);
                }
                log.error("Error downloading: {} due to: {}", gav, e.getMessage(), e);
                return;
            }
            if (!done) {
                log.info("Downloading: {} (elapsed: {})", gav, TimeUtils.printDuration(watch.taken()));
            }
        }

        long taken = watch.taken();
        for (String g : gavs) {
            MavenGav a = MavenGav.parseGav(g);
            DownloadRecord downloadRecord
                    = downloader.getDownloadState(a.getGroupId(), a.getArtifactId(), a.getVersion());
            if (downloadRecord != null) {
                String url = downloadRecord.repoUrl();
                String id = downloadRecord.repoId();
                String msg = "Downloaded: " + g + " (took: "
                             + TimeUtils.printDuration(taken, true) + ") from: " + id + "@" + url;
                log.info(msg);
            } else {
                String msg = "Resolved: " + g + " (took: "
                             + TimeUtils.printDuration(taken, true) + ")";
                if (verbose || taken > 2000) {
                    // slow resolving then log
                    log.info(msg);
                } else {
                    log.debug(msg);
                }
            }
        }
    }

    /**
     * Why the download failed: the message of Maven Resolver (such as the artifact that could not be found, and in
     * which repositories), else the message of the cause.
     */
    private static String reason(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof MavenResolutionException && t.getMessage() != null) {
                return t.getMessage();
            }
        }
        return cause.getMessage();
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
