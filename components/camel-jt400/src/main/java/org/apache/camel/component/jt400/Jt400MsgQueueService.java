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
package org.apache.camel.component.jt400;

import java.io.IOException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.MessageQueue;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pseudo-abstract class that encapsulates Service logic common to {@link Jt400MsgQueueConsumer} and
 * {@link Jt400MsgQueueProducer}.
 */
class Jt400MsgQueueService implements Service {

    /**
     * Logging tool.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Jt400MsgQueueService.class);

    /**
     * Endpoint which this service connects to.
     */
    private final Jt400Endpoint endpoint;

    /**
     * Message queue object that corresponds to the endpoint of this service (null if stopped).
     */
    private MessageQueue queue;

    /**
     * Creates a {@code Jt400MsgQueueService} that connects to the specified endpoint.
     *
     * @param endpoint endpoint which this service connects to
     */
    Jt400MsgQueueService(Jt400Endpoint endpoint) {
        ObjectHelper.notNull(endpoint, "endpoint", this);
        this.endpoint = endpoint;
    }

    @Override
    public void start() {
        if (queue == null) {
            AS400 system = endpoint.getSystem();
            queue = new MessageQueue(system, endpoint.getObjectPath());
        }
        if (!queue.getSystem().isConnected(AS400.COMMAND)) {
            LOG.debug("Connecting to {}", endpoint);
            try {
                queue.getSystem().connectService(AS400.COMMAND);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void stop() {
        if (queue != null) {
            LOG.debug("Releasing connection to {}", endpoint);
            AS400 system = queue.getSystem();
            queue = null;
            endpoint.releaseSystem(system);
        }
    }

    /**
     * Returns the message queue object that this service connects to. Returns {@code null} if the service is stopped.
     *
     * @return the message queue object that this service connects to, or {@code null} if stopped
     */
    public MessageQueue getMsgQueue() {
        return queue;
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
