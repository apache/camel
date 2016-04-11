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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.IsSingleton;
import org.apache.camel.PollingConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A {@link ConsumerCache} which is always empty and does not cache any {@link org.apache.camel.Consumer}s.
 */
public class EmptyConsumerCache extends ConsumerCache {

    public EmptyConsumerCache(Object source, CamelContext camelContext) {
        super(source, camelContext, 0);
    }

    @Override
    public PollingConsumer acquirePollingConsumer(Endpoint endpoint) {
        // always create a new consumer
        PollingConsumer answer;
        try {
            answer = endpoint.createPollingConsumer();
            boolean singleton = true;
            if (answer instanceof IsSingleton) {
                singleton = ((IsSingleton) answer).isSingleton();
            }
            if (getCamelContext().isStartingRoutes() && singleton) {
                // if we are currently starting a route, then add as service and enlist in JMX
                // - but do not enlist non-singletons in JMX
                // - note addService will also start the service
                getCamelContext().addService(answer);
            } else {
                // must then start service so producer is ready to be used
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
        try {
            ServiceHelper.stopAndShutdownService(pollingConsumer);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public String toString() {
        return "EmptyConsumerCache for source: " + getSource();
    }

}
