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
package org.apache.camel.component.ironmq;

import java.net.MalformedURLException;

import io.iron.ironmq.Client;
import io.iron.ironmq.Cloud;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ironmq provides integration with <a href="https://www.iron.io/">IronMQ</a> an elastic and durable hosted message queue as a service.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "ironmq", syntax = "ironmq:queueName", title = "IronMQ", consumerClass = IronMQConsumer.class, label = "cloud,messaging")
public class IronMQEndpoint extends ScheduledPollEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(IronMQEndpoint.class);

    @UriParam
    private IronMQConfiguration configuration;

    private Client client;

    public IronMQEndpoint(String uri, IronMQComponent component, IronMQConfiguration ironMQConfiguration) {
        super(uri, component);
        this.configuration = ironMQConfiguration;
    }

    public Producer createProducer() throws Exception {
        return new IronMQProducer(this, getClient().queue(configuration.getQueueName()));
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        IronMQConsumer ironMQConsumer = new IronMQConsumer(this, processor, getClient().queue(configuration.getQueueName()));
        configureConsumer(ironMQConsumer);
        ironMQConsumer.setMaxMessagesPerPoll(configuration.getMaxMessagesPerPoll());
        DefaultScheduledPollConsumerScheduler scheduler = new DefaultScheduledPollConsumerScheduler();
        scheduler.setConcurrentTasks(configuration.getConcurrentConsumers());
        ironMQConsumer.setScheduler(scheduler);

        return ironMQConsumer;
    }

    public Exchange createExchange(io.iron.ironmq.Message msg) {
        return createExchange(getExchangePattern(), msg);
    }

    private Exchange createExchange(ExchangePattern pattern, io.iron.ironmq.Message msg) {
        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();
        if (configuration.isPreserveHeaders()) {
            GsonUtil.copyFrom(msg, message);
        } else {
            message.setBody(msg.getBody());
        }
        message.setHeader(IronMQConstants.MESSAGE_ID, msg.getId());
        message.setHeader(IronMQConstants.MESSAGE_RESERVATION_ID, msg.getReservationId());
        message.setHeader(IronMQConstants.MESSAGE_RESERVED_COUNT, msg.getReservedCount());
        return exchange;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = getConfiguration().getClient() != null ? getConfiguration().getClient() : getClient();
    }

    @Override
    protected void doStop() throws Exception {
        client = null;
        super.doStop();
    }

    public Client getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * Provide the possibility to override this method for an mock
     * implementation
     * 
     * @return Client
     */
    Client createClient() {
        Cloud cloud;
        try {
            cloud = new Cloud(configuration.getIronMQCloud());
        } catch (MalformedURLException e) {
            cloud = Cloud.ironAWSUSEast;
            LOG.warn("Unable to parse ironMQCloud {} will use {}", configuration.getIronMQCloud(), cloud.getHost());
        }
        client = new Client(configuration.getProjectId(), configuration.getToken(), cloud);
        return client;
    }

    public IronMQConfiguration getConfiguration() {
        return configuration;
    }

}
