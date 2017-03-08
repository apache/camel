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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;

/**
 * Producer sending messages to the JGroups cluster.
 */
public class JGroupsProducer extends DefaultProducer {

    // Producer settings

    private final JGroupsEndpoint endpoint;

    private final JChannel channel;

    private final String clusterName;

    // Constructor

    public JGroupsProducer(JGroupsEndpoint endpoint, JChannel channel, String clusterName) {
        super(endpoint);

        this.endpoint = endpoint;
        this.channel = channel;
        this.clusterName = clusterName;
    }

    // Life cycle callbacks

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.connect();
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.disconnect();
        super.doStop();
    }

    // Processing logic

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body != null) {
            Address destinationAddress = exchange.getIn().getHeader(JGroupsEndpoint.HEADER_JGROUPS_DEST, Address.class);
            Address sourceAddress = exchange.getIn().getHeader(JGroupsEndpoint.HEADER_JGROUPS_SRC, Address.class);

            log.debug("Posting: {} to cluster: {}", body, clusterName);
            if (destinationAddress != null) {
                log.debug("Posting to custom destination address: {}", destinationAddress);
            }
            if (sourceAddress != null) {
                log.debug("Posting from custom source address: {}", sourceAddress);
            }
            Message message = new Message(destinationAddress, body);
            message.setSrc(sourceAddress);
            channel.send(message);
        } else {
            log.debug("Body is null, cannot post to channel.");
        }
    }

}
