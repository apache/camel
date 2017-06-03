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
package org.apache.camel.component.jt400;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.BaseDataQueue;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.KeyedDataQueue;
import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pseudo-abstract class that encapsulates Service logic common to
 * {@link Jt400DataQueueConsumer} and {@link Jt400DataQueueProducer}.
 */
class Jt400DataQueueService implements Service {
    
    /**
     * Logging tool.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Jt400DataQueueService.class);
    
    /**
     * Endpoint which this service connects to.
     */
    private final Jt400Endpoint endpoint;
    
    /**
     * Data queue object that corresponds to the endpoint of this service (null if stopped).
     */
    private BaseDataQueue queue;
    
    /**
     * Creates a {@code Jt400DataQueueService} that connects to the specified
     * endpoint.
     * 
     * @param endpoint endpoint which this service connects to
     */
    Jt400DataQueueService(Jt400Endpoint endpoint) {
        ObjectHelper.notNull(endpoint, "endpoint", this);
        this.endpoint = endpoint;
    }

    @Override
    public void start() throws Exception {
        if (queue == null) {
            AS400 system = endpoint.getSystem();
            if (endpoint.isKeyed()) {
                queue = new KeyedDataQueue(system, endpoint.getObjectPath());
            } else {
                queue = new DataQueue(system, endpoint.getObjectPath());
            }
        }
        if (!queue.getSystem().isConnected(AS400.DATAQUEUE)) {
            LOG.info("Connecting to {}", endpoint);
            queue.getSystem().connectService(AS400.DATAQUEUE);
        }
    }

    @Override
    public void stop() throws Exception {
        if (queue != null) {
            LOG.info("Releasing connection to {}", endpoint);
            AS400 system = queue.getSystem();
            queue = null;
            endpoint.releaseSystem(system);
        }
    }
    
    /**
     * Returns the data queue object that this service connects to. Returns
     * {@code null} if the service is stopped.
     * 
     * @return the data queue object that this service connects to, or
     *         {@code null} if stopped
     */
    public BaseDataQueue getDataQueue() {
        return queue;
    }

}
