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

import static org.apache.camel.component.couchdb.CouchDbConstants.COUCHDB_RESUME_ACTION;

import java.util.Queue;
import java.util.concurrent.ExecutorService;

import com.google.gson.JsonParser;
import com.ibm.cloud.cloudant.v1.model.ChangesResult;
import com.ibm.cloud.cloudant.v1.model.ChangesResultItem;
import com.ibm.cloud.sdk.core.http.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CouchDbConsumer extends ScheduledBatchPollingConsumer implements ResumeAware<ResumeStrategy> {
    private static final Logger LOG = LoggerFactory.getLogger(CouchDbConsumer.class);

    private final CouchDbClientWrapper couchClient;
    private final CouchDbEndpoint endpoint;
    private ExecutorService executor;
    private ResumeStrategy resumeStrategy;
    private String since;
    private String lastSequence = null;

    public CouchDbConsumer(CouchDbEndpoint endpoint, CouchDbClientWrapper couchClient, Processor processor) {
        super(endpoint, processor);
        this.couchClient = couchClient;
        this.endpoint = endpoint;

        since = couchClient.getLatestUpdateSequence();
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    public Exchange createExchange(String seq, String id, ChangesResultItem changesResultItem, boolean deleted) {
        Exchange exchange = createExchange(false);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DATABASE, endpoint.getDatabase());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_SEQ, seq);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, id);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_METHOD, deleted ? "DELETE" : "UPDATE");
        exchange.getIn().setBody(new JsonParser().parseString(changesResultItem.toString()));
        return exchange;
    }

    @Override
    protected int poll() throws Exception {
        Response<ChangesResult> changesResultResponse =
                couchClient.pollChanges(endpoint.getStyle(), since, endpoint.getHeartbeat(), getMaxMessagesPerPoll());

        for (ChangesResultItem changesResultItem :
                changesResultResponse.getResult().getResults()) {
            if (changesResultItem.isDeleted() != null) {
                if (changesResultItem.isDeleted() && !endpoint.isDeletes()) {
                    continue;
                }
                if (!changesResultItem.isDeleted() && !endpoint.isUpdates()) {
                    continue;
                }
            }

            lastSequence = changesResultItem.getSeq();

            Exchange exchange = this.createExchange(
                    lastSequence,
                    changesResultItem.getId(),
                    changesResultItem,
                    changesResultItem.isDeleted() == null ? false : changesResultItem.isDeleted());

            if (LOG.isTraceEnabled()) {
                LOG.trace(
                        "Created exchange [exchange={}, _id={}, seq={}",
                        exchange,
                        changesResultItem.getId(),
                        lastSequence);
            }

            try {
                this.getProcessor().process(exchange);
            } catch (Exception e) {
                this.getExceptionHandler().handleException("Error processing exchange.", exchange, e);
            } finally {
                // Update since with latest seq, the messages are ordered
                since = changesResultItem.getSeq();
                this.releaseExchange(exchange, false);
            }
        }

        return changesResultResponse.getResult().getResults().size();
    }

    @Override
    protected void doStart() throws Exception {
        ResumeStrategyHelper.resume(getEndpoint().getCamelContext(), this, resumeStrategy, COUCHDB_RESUME_ACTION);

        super.doStart();
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        return 0;
    }
}
