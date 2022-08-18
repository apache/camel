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

package org.apache.camel.test.infra.activemq.common;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.test.infra.activemq.services.AbstractActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.apache.camel.test.infra.activemq.services.ActiveMQServiceFactory;
import org.apache.camel.test.infra.activemq.services.ConnectionFactoryAware;

public final class ConnectionFactoryHelper {
    private ConnectionFactoryHelper() {
    }

    public static ConnectionFactory createConnectionFactory(ActiveMQService service) {
        return createConnectionFactory(service, null);
    }

    public static ConnectionFactory createConnectionFactory(ActiveMQService service, Integer maximumRedeliveries) {
        if (service instanceof ConnectionFactoryAware) {
            return createConnectionFactory(((AbstractActiveMQEmbeddedService) service).getVmURL(), maximumRedeliveries);
        }

        if (service instanceof ActiveMQServiceFactory.SingletonActiveMQService) {
            return createConnectionFactory(((ActiveMQServiceFactory.SingletonActiveMQService) service).getService(),
                    maximumRedeliveries);
        }

        throw new UnsupportedOperationException("The test service does not implement ConnectionFactoryAware");
    }

    public static ConnectionFactory createConnectionFactory(String url, Integer maximumRedeliveries) {
        return createConnectionFactory(new ActiveMQConnectionFactory(url), maximumRedeliveries);
    }

    public static ConnectionFactory createConnectionFactory(
            ActiveMQConnectionFactory connectionFactory, Integer maximumRedeliveries) {
        // optimize AMQ to be as fast as possible so unit testing is quicker
        connectionFactory.setCopyMessageOnSend(false);
        connectionFactory.setOptimizeAcknowledge(true);
        connectionFactory.setOptimizedMessageDispatch(true);

        // When using asyncSend, producers will not be guaranteed to send in the order we
        // have in the tests (which may be confusing for queues) so we need this set to false.
        // Another way of guaranteeing order is to use persistent messages or transactions.
        connectionFactory.setUseAsyncSend(false);
        connectionFactory.setAlwaysSessionAsync(false);
        if (maximumRedeliveries != null) {
            connectionFactory.getRedeliveryPolicy().setMaximumRedeliveries(maximumRedeliveries);
        }
        connectionFactory.setTrustAllPackages(true);
        return connectionFactory;
    }

    public static ConnectionFactory createPersistentConnectionFactory(String url) {
        return createPersistentConnectionFactory(new ActiveMQConnectionFactory(url));

    }

    public static ConnectionFactory createPersistentConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
        connectionFactory.setCopyMessageOnSend(false);
        connectionFactory.setOptimizeAcknowledge(true);
        connectionFactory.setOptimizedMessageDispatch(true);
        connectionFactory.setAlwaysSessionAsync(false);
        connectionFactory.setTrustAllPackages(true);
        return connectionFactory;
    }
}
