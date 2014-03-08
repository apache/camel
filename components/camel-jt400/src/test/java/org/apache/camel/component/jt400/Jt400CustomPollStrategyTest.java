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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumer;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.junit.Test;

public class Jt400CustomPollStrategyTest extends Jt400TestSupport {

    private static final String PASSWORD = "p4ssw0rd";
    private PollingConsumerPollStrategy poll = new MyPollStrategy();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("jt400PollStrategy", poll);
        return jndi;
    }

    @Test
    public void testCustomPollStrategy() throws Exception {
        Jt400DataQueueEndpoint endpoint = resolveMandatoryEndpoint(
                "jt400://user:" + PASSWORD + "@host/qsys.lib/library.lib/queue.dtaq?connectionPool=#mockPool&pollStrategy=#jt400PollStrategy",
                Jt400DataQueueEndpoint.class);
        assertNotNull(endpoint);

        PollingConsumer consumer = endpoint.createPollingConsumer();
        assertNotNull(consumer);
    }

    private static final class MyPollStrategy implements PollingConsumerPollStrategy {

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int i) {
            // noop
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int i, Exception e) throws Exception {
            return false;
        }
    }
}
