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
package org.apache.camel.util;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Some helper methods for working with {@link Endpoint} instances
 *
 * @version $Revision$
 */
public final class EndpointHelper {
    private static final transient Log LOG = LogFactory.getLog(EndpointHelper.class);

    private EndpointHelper() {
        //Utility Class
    }
    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the endpoint
     * and invokes the given {@link Processor} to process each {@link Exchange} and then closes
     * down the consumer and throws any exceptions thrown.
     *
     * @param endpoint
     * @param processor
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor, long timeout) throws Exception {
        PollingConsumer consumer = endpoint.createPollingConsumer();
        try {
            consumer.start();

            while (true) {
                Exchange exchange = consumer.receive(timeout);
                if (exchange == null) {
                    break;
                } else {
                    processor.process(exchange);
                }
            }
        } finally {
            try {
                consumer.stop();
            } catch (Exception e) {
                LOG.warn("Failed to stop PollingConsumer: " + e, e);
            }
        }
    }

    /**
     * Creates a {@link PollingConsumer} and polls all pending messages on the
     * endpoint and invokes the given {@link Processor} to process each
     * {@link Exchange} and then closes down the consumer and throws any
     * exceptions thrown.
     *
     * @param endpoint
     * @param processor
     */
    public static void pollEndpoint(Endpoint endpoint, Processor processor) throws Exception {
        pollEndpoint(endpoint, processor, 1000L);
    }
}
