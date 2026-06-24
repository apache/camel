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
package org.apache.camel.component.mongodb;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeAware;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.resume.ResumeStrategyHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonList;

/**
 * The MongoDb Change Streams consumer.
 */
public class MongoDbChangeStreamsConsumer extends DefaultConsumer implements ResumeAware<ResumeStrategy> {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbChangeStreamsConsumer.class);
    private static final String MONGODB_RESUME_PLACEHOLDER_ACTION = "mongodb-resume";

    private final MongoDbEndpoint endpoint;
    private ExecutorService executor;
    private MongoDbChangeStreamsThread changeStreamsThread;
    private ResumeStrategy resumeStrategy;
    private boolean stopOffsetRepo;
    private volatile BsonDocument startupResumeToken;

    public MongoDbChangeStreamsConsumer(MongoDbEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
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

    @Override
    public String adapterFactoryService() {
        return "mongodb-adapter-factory";
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (changeStreamsThread != null) {
            changeStreamsThread.stop();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }

        if (stopOffsetRepo) {
            StateRepository<String, String> repo = endpoint.getChangeStreamTokenRepository();
            LOG.debug("Stopping ChangeStreamTokenRepository: {}", repo);
            ServiceHelper.stopAndShutdownService(repo);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Is the change stream token repository already started?
        StateRepository<String, String> repo = endpoint.getChangeStreamTokenRepository();
        if (repo instanceof ServiceSupport serviceSupport) {
            boolean started = serviceSupport.isStarted();
            // If not already started then start and mark to stop when stopping the consumer
            if (!started) {
                stopOffsetRepo = true;
                LOG.debug("Starting ChangeStreamTokenRepository: {}", repo);
                ServiceHelper.startService(endpoint.getChangeStreamTokenRepository());
            }
        }

        String streamFilter = endpoint.getStreamFilter();
        List<BsonDocument> bsonFilter = null;
        if (ObjectHelper.isNotEmpty(streamFilter)) {
            bsonFilter = singletonList(BsonDocument.parse(streamFilter));
        }

        if (resumeStrategy != null) {
            ResumeAdapter resumeAdapter = resumeStrategy.getAdapter();
            if (resumeAdapter instanceof MongoDbResumeAdapter adapter) {
                adapter.setResumeTokenKey(getResumeTokenKey());
                adapter.setConsumer(this);
            }

            ResumeStrategyHelper.resume(getEndpoint().getCamelContext(), this, resumeStrategy,
                    MONGODB_RESUME_PLACEHOLDER_ACTION);
        }

        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                endpoint.getEndpointUri(), 1);
        changeStreamsThread = new MongoDbChangeStreamsThread(endpoint, this, bsonFilter);
        changeStreamsThread.init();
        executor.execute(changeStreamsThread);
    }

    BsonDocument getStartupResumeToken() {
        return startupResumeToken;
    }

    void setStartupResumeToken(BsonDocument startupResumeToken) {
        this.startupResumeToken = startupResumeToken;
    }

    String getResumeTokenKey() {
        String routeId = getRouteId();
        if (ObjectHelper.isEmpty(routeId)) {
            routeId = endpoint.getEndpointUri();
        }
        return routeId + '/' + endpoint.getCollection();
    }

}
