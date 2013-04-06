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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.ProcessorEndpoint;

/**
 * @version 
 */
public class BeanComponentCustomCreateEndpointTest extends ContextTestSupport {

    public void testCreateEndpoint() throws Exception {
        BeanComponent bc = context.getComponent("bean", BeanComponent.class);
        ProcessorEndpoint pe = bc.createEndpoint(new MyFooBean());
        assertNotNull(pe);

        String uri = pe.getEndpointUri();
        assertEquals("bean:generated:MyFooBean", uri);

        Producer producer = pe.createProducer();
        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("World");

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertEquals("Hello World", exchange.getOut().getBody());
    }

    public void testCreateEndpointUri() throws Exception {
        BeanComponent bc = context.getComponent("bean", BeanComponent.class);
        ProcessorEndpoint pe = bc.createEndpoint(new MyFooBean(), "bean:cheese");
        assertNotNull(pe);

        String uri = pe.getEndpointUri();
        assertEquals("bean:cheese", uri);

        Producer producer = pe.createProducer();
        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("World");

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertEquals("Hello World", exchange.getOut().getBody());
    }

}
