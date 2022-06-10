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

import java.util.concurrent.ExecutorService;

import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;

import static org.apache.camel.component.couchdb.CouchDbConstants.COUCHDB_RESUME_ACTION;

public class CouchDbConsumer extends DefaultConsumer implements ResumeAware<ResumeStrategy> {

    private final CouchDbClientWrapper couchClient;
    private final CouchDbEndpoint endpoint;
    private ExecutorService executor;
    private CouchDbChangesetTracker task;
    private ResumeStrategy resumeStrategy;

    public CouchDbConsumer(CouchDbEndpoint endpoint, CouchDbClientWrapper couchClient, Processor processor) {
        super(endpoint, processor);
        this.couchClient = couchClient;
        this.endpoint = endpoint;
    }

    @Override
    public void setResumeStrategy(ResumeStrategy resumeStrategy) {
        this.resumeStrategy = resumeStrategy;
    }

    @Override
    public ResumeStrategy getResumeStrategy() {
        return resumeStrategy;
    }

    public Exchange createExchange(String seq, String id, JsonObject obj, boolean deleted) {
        Exchange exchange = createExchange(false);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DATABASE, endpoint.getDatabase());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_SEQ, seq);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_ID, id);
        exchange.getIn().setHeader(CouchDbConstants.HEADER_DOC_REV, obj.get("_rev").getAsString());
        exchange.getIn().setHeader(CouchDbConstants.HEADER_METHOD, deleted ? "DELETE" : "UPDATE");
        exchange.getIn().setBody(obj);
        return exchange;
    }

    @Override
    protected void doStart() throws Exception {
        ResumeStrategyHelper.resume(getEndpoint().getCamelContext(), this, resumeStrategy, COUCHDB_RESUME_ACTION);

        super.doStart();

        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(),
                1);
        task = new CouchDbChangesetTracker(endpoint, this, couchClient);
        executor.submit(task);

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (task != null) {
            task.stop();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

}
