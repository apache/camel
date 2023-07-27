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
package org.apache.camel.component.jgroups;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer sending messages to the JGroups cluster.
 */
public class JGroupsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsProducer.class);

    // Producer settings

    private final JGroupsEndpoint endpoint;

    private final String clusterName;

    // Constructor

    public JGroupsProducer(JGroupsEndpoint endpoint, String clusterName) {
        super(endpoint);

        this.endpoint = endpoint;
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
            Address destinationAddress = exchange.getIn().getHeader(JGroupsConstants.HEADER_JGROUPS_DEST, Address.class);
            Address sourceAddress = exchange.getIn().getHeader(JGroupsConstants.HEADER_JGROUPS_SRC, Address.class);

            LOG.debug("Posting: {} to cluster: {}", body, clusterName);
            if (destinationAddress != null) {
                LOG.debug("Posting to custom destination address: {}", destinationAddress);
            }
            if (sourceAddress != null) {
                LOG.debug("Posting from custom source address: {}", sourceAddress);
            }
            Message message = new ObjectMessage(destinationAddress, body);
            message.setSrc(sourceAddress);
            endpoint.getResolvedChannel().send(message);
        } else {
            LOG.debug("Body is null, cannot post to channel.");
        }
    }

}
