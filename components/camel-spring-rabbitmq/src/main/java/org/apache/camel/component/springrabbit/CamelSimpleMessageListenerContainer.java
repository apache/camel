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

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

public class CamelSimpleMessageListenerContainer extends SimpleMessageListenerContainer implements MessageListenerContainer {

    public CamelSimpleMessageListenerContainer(SpringRabbitMQEndpoint endpoint) {
        super(endpoint.getConnectionFactory());
        this.setConcurrentConsumers(endpoint.getConcurrentConsumers());
        if (endpoint.getMaxConcurrentConsumers() != null) {
            this.setMaxConcurrentConsumers(endpoint.getMaxConcurrentConsumers());
        }
    }

    @Override
    public AmqpAdmin getAmqpAdmin() {
        return super.getAmqpAdmin();
    }
}
