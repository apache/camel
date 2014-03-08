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
package org.apache.camel.component.spring.ws.bean;

import org.springframework.util.Assert;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.server.endpoint.mapping.AbstractMapBasedEndpointMapping;

/**
 * Spring {@link MessageEndpoint} for dispatching messages received by Spring-WS to a Camel
 * endpoint. This class needs to be registered in the Spring <tt>ApplicationContext</tt>
 * when consuming messages using the <tt>spring-ws:beanname:</tt> URI scheme.
 * <p/>
 * For example, when using a route such as <tt>from("spring-ws:beanname:stockQuote").to("...");</tt>
 * the following bean definition needs to be present in the <tt>ApplicationContext</tt>:
 * <p/>
 * {@code
 * <bean id="stockQuote" class="org.apache.camel.component.spring.ws.bean.CamelEndpointDispatcher" />
 * }
 *
 * @see AbstractMapBasedEndpointMapping#setMappings(java.util.Properties)
 * @see AbstractMapBasedEndpointMapping#setEndpointMap(java.util.Map)
 */
public class CamelEndpointDispatcher implements MessageEndpoint {

    private MessageEndpoint consumerMessageEndpoint;

    public void invoke(MessageContext messageContext) throws Exception {
        Assert.notNull(consumerMessageEndpoint);
        consumerMessageEndpoint.invoke(messageContext);
    }

    public MessageEndpoint getConsumerMessageEndpoint() {
        return consumerMessageEndpoint;
    }

    public void setConsumerMessageEndpoint(MessageEndpoint consumer) {
        this.consumerMessageEndpoint = consumer;
    }
}
