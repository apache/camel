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
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.bson.BsonDocument;

import static java.util.Collections.singletonList;

/**
 * The MongoDb Change Streams consumer.
 */
public class MongoDbChangeStreamsConsumer extends DefaultConsumer {

    private final MongoDbEndpoint endpoint;
    private ExecutorService executor;
    private MongoDbChangeStreamsThread changeStreamsThread;

    public MongoDbChangeStreamsConsumer(MongoDbEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
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
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String streamFilter = endpoint.getStreamFilter();
        List<BsonDocument> bsonFilter = null;
        if (ObjectHelper.isNotEmpty(streamFilter)) {
            bsonFilter = singletonList(BsonDocument.parse(streamFilter));
        }

        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                endpoint.getEndpointUri(), 1);
        changeStreamsThread = new MongoDbChangeStreamsThread(endpoint, this, bsonFilter);
        changeStreamsThread.init();
        executor.execute(changeStreamsThread);
    }

}
