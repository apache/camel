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
package org.apache.camel.component.hazelcast.instance;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.hazelcast.HazelcastComponentHelper;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultEndpoint;

public class HazelcastInstanceConsumer extends DefaultConsumer {

    public HazelcastInstanceConsumer(DefaultEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        Hazelcast.getCluster().addMembershipListener(new HazelcastMembershipListener());
    }

    class HazelcastMembershipListener implements MembershipListener {

        public void memberAdded(MembershipEvent event) {
            this.sendExchange(event, HazelcastConstants.ADDED);
        }

        public void memberRemoved(MembershipEvent event) {
            this.sendExchange(event, HazelcastConstants.REMOVED);
        }

        private void sendExchange(MembershipEvent event, String action) {
            Exchange exchange = getEndpoint().createExchange();

            HazelcastComponentHelper.setListenerHeaders(exchange, HazelcastConstants.INSTANCE_LISTENER, action);

            // instance listener header values
            exchange.getOut().setHeader(HazelcastConstants.INSTANCE_HOST, event.getMember().getInetSocketAddress().getHostName());
            exchange.getOut().setHeader(HazelcastConstants.INSTANCE_PORT, event.getMember().getInetSocketAddress().getPort());

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange for hazelcast consumer on your Hazelcast cluster.", exchange, exchange.getException());
                }
            }
        }

    }

}
