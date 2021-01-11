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
package org.apache.camel.component.springrabbit;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;

@Component("spring-rabbitmq")
public class RabbitMQComponent extends HeaderFilterStrategyComponent {

    @Metadata(autowired = true,
              description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @Metadata(autowired = true,
              description = "Optional AMQP Admin service to use for auto declaring elements (queues, exchanges, bindings)")
    private AmqpAdmin amqpAdmin;
    @Metadata(description = "Specifies whether to test the connection on startup."
                            + " This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker."
                            + " If a connection cannot be granted then Camel throws an exception on startup."
                            + " This ensures that Camel is not started with failed connections."
                            + " The JMS producers is tested as well.")
    private boolean testConnectionOnStartup;
    @Metadata(label = "consumer", defaultValue = "true",
              description = "Specifies whether the consumer container should auto-startup.")
    private boolean autoStartup = true;
    @Metadata(label = "advanced",
              description = "To use a custom MessageConverter so you can be in control how to map to/from a org.springframework.amqp.core.Message.")
    private MessageConverter messageConverter;
    @Metadata(label = "advanced",
              description = "To use a custom MessagePropertiesConverter so you can be in control how to map to/from a org.springframework.amqp.core.MessageProperties.")
    private MessagePropertiesConverter messagePropertiesConverter;
    @Metadata(label = "consumer", description = "The name of the dead letter exchange")
    private String deadLetterExchange;
    @Metadata(label = "consumer", description = "The name of the dead letter queue")
    private String deadLetterQueue;
    @Metadata(label = "consumer", description = "The routing key for the dead letter exchange")
    private String deadLetterRoutingKey;
    @Metadata(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the dead letter exchange")
    private String deadLetterExchangeType = "direct";

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (getHeaderFilterStrategy() == null) {
            setHeaderFilterStrategy(new RabbitMQHeaderFilterStrategy());
        }
        if (messageConverter == null) {
            messageConverter = new DefaultMessageConverter(getCamelContext());
        }
        if (messagePropertiesConverter == null) {
            messagePropertiesConverter = new DefaultMessagePropertiesConverter(getCamelContext(), getHeaderFilterStrategy());
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RabbitMQEndpoint endpoint = new RabbitMQEndpoint(uri, this, remaining);
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setTestConnectionOnStartup(testConnectionOnStartup);
        endpoint.setMessageConverter(messageConverter);
        endpoint.setMessagePropertiesConverter(messagePropertiesConverter);
        endpoint.setAutoStartup(autoStartup);
        endpoint.setDeadLetterExchange(deadLetterExchange);
        endpoint.setDeadLetterExchangeType(deadLetterExchangeType);
        endpoint.setDeadLetterQueue(deadLetterQueue);
        endpoint.setDeadLetterRoutingKey(deadLetterRoutingKey);

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public AmqpAdmin getAmqpAdmin() {
        return amqpAdmin;
    }

    public void setAmqpAdmin(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public MessageConverter getMessageConverter() {
        return messageConverter;
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    public MessagePropertiesConverter getMessagePropertiesConverter() {
        return messagePropertiesConverter;
    }

    public void setMessagePropertiesConverter(MessagePropertiesConverter messagePropertiesConverter) {
        this.messagePropertiesConverter = messagePropertiesConverter;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public String getDeadLetterExchangeType() {
        return deadLetterExchangeType;
    }

    public void setDeadLetterExchangeType(String deadLetterExchangeType) {
        this.deadLetterExchangeType = deadLetterExchangeType;
    }
}
