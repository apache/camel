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
package org.apache.camel.support.cache;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A {@link org.apache.camel.spi.ConsumerCache} that does not cache {@link PollingConsumer}s but instead creates a new
 * consumer on every {@link #acquirePollingConsumer(Endpoint)} and stops and shuts it down on
 * {@link #releasePollingConsumer(Endpoint, PollingConsumer)}.
 *
 * @since 4.21
 */
public class EmptyConsumerCache extends DefaultConsumerCache {

    private final Object source;
    private final CamelContext ecc;

    public EmptyConsumerCache(Object source, CamelContext camelContext) {
        super(source, camelContext, -1);
        this.source = source;
        this.ecc = camelContext;
        setExtendedStatistics(false);
    }

    @Override
    public PollingConsumer acquirePollingConsumer(Endpoint endpoint) {
        // always create a new consumer
        PollingConsumer answer;
        try {
            answer = endpoint.createPollingConsumer();
            boolean startingRoutes
                    = ecc.getCamelContextExtension().isSetupRoutes() || ecc.getRouteController().isStartingRoutes();
            if (startingRoutes) {
                // if we are currently starting a route, then add as service and enlist in JMX
                getCamelContext().addService(answer);
            } else {
                // must then start service so consumer is ready to be used
                ServiceHelper.startService(answer);
            }
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(endpoint, e);
        }
        return answer;
    }

    @Override
    public void releasePollingConsumer(Endpoint endpoint, PollingConsumer pollingConsumer) {
        // stop and shutdown the consumer as its not cache or reused
        ServiceHelper.stopAndShutdownService(pollingConsumer);
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public String toString() {
        return "EmptyConsumerCache for source: " + source;
    }
}
