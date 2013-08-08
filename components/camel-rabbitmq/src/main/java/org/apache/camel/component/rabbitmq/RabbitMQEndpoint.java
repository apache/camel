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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;

public class RabbitMQEndpoint extends DefaultEndpoint {

    private String username;
    private String password;
    private String vhost;
    private String hostname;
    private int threadPoolSize = 10;
    private int portNumber;
    private boolean autoAck = true;
    private boolean autoDelete = true;
    private boolean durable = true;
    private String queue = String.valueOf(UUID.randomUUID().toString().hashCode());
    private String exchangeName;
    private String routingKey;
    
    public RabbitMQEndpoint() {
    }

    public RabbitMQEndpoint(String endpointUri, RabbitMQComponent component) throws URISyntaxException {
        super(endpointUri, component);
    }

    public Exchange createRabbitExchange(Envelope envelope, byte[] body) {
        Exchange exchange = new DefaultExchange(getCamelContext(), getExchangePattern());

        Message message = new DefaultMessage();
        exchange.setIn(message);

        message.setHeader(RabbitMQConstants.ROUTING_KEY, envelope.getRoutingKey());
        message.setHeader(RabbitMQConstants.EXCHANGE_NAME, envelope.getExchange());
        message.setHeader(RabbitMQConstants.DELIVERY_TAG, envelope.getDeliveryTag());
        message.setBody(body);

        return exchange;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Connection connect(ExecutorService executor) throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(getUsername());
        factory.setPassword(getPassword());
        if (getVhost() == null) {
            factory.setVirtualHost("/");
        } else {
            factory.setVirtualHost(getVhost());
        }
        factory.setHost(getHostname());
        factory.setPort(getPortNumber());
        return factory.newConnection(executor);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RabbitMQProducer(this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    protected ExecutorService createExecutor() {
        if (getCamelContext() != null) {
            return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "RabbitMQConsumer", getThreadPoolSize());
        } else {
            return Executors.newFixedThreadPool(getThreadPoolSize());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }
}
