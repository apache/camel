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
package org.apache.camel.component.jgroups;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.jgroups.JChannel;

/**
 * Consumes messages from the JGroups channels ({@code org.jgroups.Channel}). Received messages
 * ({@code org.jgroups.Message}) are routed to Camel as the body of {@link org.apache.camel.Exchange}.
 */
public class JGroupsConsumer extends DefaultConsumer {

    private final JChannel channel;
    private final String clusterName;

    private final CamelJGroupsReceiver receiver;
    private final JGroupsEndpoint endpoint;

    public JGroupsConsumer(JGroupsEndpoint endpoint, Processor processor, JChannel channel, String clusterName) {
        super(endpoint, processor);

        this.endpoint = endpoint;
        this.channel = channel;
        this.clusterName = clusterName;

        this.receiver = new CamelJGroupsReceiver(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.debug("Connecting receiver: {} to the cluster: {}.", receiver, clusterName);
        channel.setReceiver(receiver);
        endpoint.connect();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("Closing connection to cluster: {} from receiver: {}.", clusterName, receiver);
        channel.setReceiver(null);
        endpoint.disconnect();
        super.doStop();
    }
}
