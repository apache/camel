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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jgroups.JGroupsConstants.HEADER_JGROUPS_CHANNEL_ADDRESS;

/**
 * Implementation of JGroups message receiver ({@code org.jgroups.Receiver}) wrapping incoming messages into Camel
 * exchanges. Used by {@link JGroupsConsumer}.
 */
public class CamelJGroupsReceiver implements Receiver {

    private static final Logger LOG = LoggerFactory.getLogger(CamelJGroupsReceiver.class);

    private final JGroupsConsumer consumer;
    private final JGroupsEndpoint endpoint;
    private final AsyncProcessor processor;

    public CamelJGroupsReceiver(JGroupsConsumer consumer, JGroupsEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public void viewAccepted(View view) {
        if (endpoint.isEnableViewMessages()) {
            Exchange exchange = createExchange(view);
            try {
                LOG.debug("Processing view: {}", view);
                processor.process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        // noop
                    }
                });
            } catch (Exception e) {
                throw new JGroupsException("Error in consumer while dispatching exchange containing view " + view, e);
            }
        } else {
            LOG.debug("Option enableViewMessages is set to false. Skipping processing of the view: {}", view);
        }
    }

    @Override
    public void receive(Message message) {
        Exchange exchange = endpoint.createExchange(message);
        try {
            LOG.debug("Processing message: {}", message);
            processor.process(exchange, doneSync -> {
                // noop
            });
        } catch (Exception e) {
            throw new JGroupsException("Error in consumer while dispatching exchange containing message " + message, e);
        }
    }

    public Exchange createExchange(View view) {
        Exchange exchange = consumer.createExchange(true);
        exchange.getIn().setHeader(HEADER_JGROUPS_CHANNEL_ADDRESS, endpoint.getResolvedChannel().getAddress());
        exchange.getIn().setBody(view);
        return exchange;
    }

}
