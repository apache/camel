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
package org.apache.camel.component.mongodb;

import java.util.concurrent.ExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * The MongoDb consumer.
 */
public class MongoDbTailableCursorConsumer extends DefaultConsumer {
    private final MongoDbEndpoint endpoint;
    private ExecutorService executor;
    private MongoDbTailingProcess tailingProcess;

    public MongoDbTailableCursorConsumer(MongoDbEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (tailingProcess != null) {
            tailingProcess.stop();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), 1);
        MongoDbTailTrackingManager trackingManager = initTailTracking();
        tailingProcess = new MongoDbTailingProcess(endpoint, this, trackingManager);
        tailingProcess.initializeProcess();
        executor.execute(tailingProcess);
    }
    
    protected MongoDbTailTrackingManager initTailTracking() throws Exception {
        MongoDbTailTrackingManager answer = new MongoDbTailTrackingManager(endpoint.getMongoConnection(), endpoint.getTailTrackingConfig());
        answer.initialize();
        return answer;
    }
    
}
