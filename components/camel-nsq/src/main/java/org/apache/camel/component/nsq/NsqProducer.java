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
package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.ServerAddress;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The nsq producer.
 */
public class NsqProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(NsqProducer.class);

    private NSQProducer producer;
    private final NsqConfiguration configuration;

    public NsqProducer(NsqEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    public NsqEndpoint getEndpoint() {
        return (NsqEndpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String topic = exchange.getIn().getHeader(NsqConstants.NSQ_MESSAGE_TOPIC, configuration.getTopic(), String.class);

        LOG.debug("Publishing to topic: {}", topic);
        byte[] body = exchange.getIn().getBody(byte[].class);
        producer.produce(topic, body);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting NSQ Producer");

        NsqConfiguration config = getEndpoint().getConfiguration();
        producer = new NSQProducer();
        for (ServerAddress server : config.getServerAddresses()) {
            producer.addAddress(server.getHost(), server.getPort() == 0 ? config.getPort() : server.getPort());
        }
        producer.setConfig(getEndpoint().getNsqConfig());
        producer.start();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping NSQ Producer");
        if (producer != null) {
            producer.shutdown();
        }
        super.doStop();
    }
}
