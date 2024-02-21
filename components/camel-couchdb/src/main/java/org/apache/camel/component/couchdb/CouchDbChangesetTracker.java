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
package org.apache.camel.component.couchdb;

import java.time.Duration;

import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.lightcouch.Changes;
import org.lightcouch.ChangesResult;
import org.lightcouch.CouchDbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchDbChangesetTracker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CouchDbChangesetTracker.class);
    private static final int MAX_DB_ERROR_REPEATS = 8;

    private volatile boolean stopped;
    private final CouchDbClientWrapper couchClient;
    private final CouchDbEndpoint endpoint;
    private final CouchDbConsumer consumer;
    private Changes changes;

    public CouchDbChangesetTracker(CouchDbEndpoint endpoint, CouchDbConsumer consumer, CouchDbClientWrapper couchClient) {
        this.endpoint = endpoint;
        this.consumer = consumer;
        this.couchClient = couchClient;
    }

    private void initChanges(final String sequence) {
        String since = sequence;
        if (null == since) {
            since = couchClient.getLatestUpdateSequence();
        }
        changes = couchClient.changes().style(endpoint.getStyle()).includeDocs(true)
                .since(since).heartBeat(endpoint.getHeartbeat()).continuousChanges();
    }

    @Override
    public void run() {
        String lastSequence = null;
        initChanges(null);

        try {
            while (!stopped) {

                try {
                    while (changes.hasNext()) { // blocks until a feed is received
                        ChangesResult.Row feed = changes.next();
                        if (feed.isDeleted() && !endpoint.isDeletes()) {
                            continue;
                        }
                        if (!feed.isDeleted() && !endpoint.isUpdates()) {
                            continue;
                        }

                        lastSequence = feed.getSeq();
                        JsonObject doc = feed.getDoc();

                        Exchange exchange = consumer.createExchange(lastSequence, feed.getId(), doc, feed.isDeleted());

                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Created exchange [exchange={}, _id={}, seq={}", exchange, feed.getId(), lastSequence);
                        }

                        try {
                            consumer.getProcessor().process(exchange);
                        } catch (Exception e) {
                            consumer.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
                        } finally {
                            consumer.releaseExchange(exchange, false);
                        }
                    }

                    stopped = true;

                } catch (CouchDbException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("CouchDb Exception encountered waiting for changes!  Attempting to recover...", e);
                    }
                    if (endpoint.isRunAllowed() || !endpoint.isShutdown() || !consumer.isStopped()) {
                        if (!waitForStability(lastSequence)) {
                            throw e;
                        }
                    } else {
                        LOG.debug("Skipping the stability check because shutting down or running is not allowed at the moment");
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Unexpected error causing CouchDb change tracker to exit!", e);
        }
    }

    private boolean waitForStability(final String lastSequence) {
        BlockingTask task = Tasks.foregroundTask()
                .withBudget(Budgets.iterationBudget()
                        .withMaxIterations(MAX_DB_ERROR_REPEATS)
                        .withInterval(Duration.ofSeconds(3))
                        .build())
                .withName("couchdb-wait-for-stability")
                .build();

        return task.run(this::stabilityCheck, lastSequence);
    }

    private boolean stabilityCheck(String lastSequence) {
        try {
            // Fail fast operation
            couchClient.context().serverVersion();
            // reset change listener
            initChanges(lastSequence);

            return true;
        } catch (Exception e) {
            LOG.debug("Failed to get CouchDb server version and/or reset change listener", e);
        }

        return false;
    }

    public void stop() {
        changes.stop();
    }
}
