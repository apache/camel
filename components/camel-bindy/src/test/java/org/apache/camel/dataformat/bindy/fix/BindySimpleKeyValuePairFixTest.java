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
package org.apache.camel.dataformat.bindy.fix;

import java.util.Collections;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.KeyValuePairField;
import org.apache.camel.dataformat.bindy.annotation.Message;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleKeyValuePairFixTest extends AbstractJUnit4SpringContextTests {
    private static final String[] FIX_REQUESTS = new String[] {
        "8=FIX.4.1 37=1 38=1 40=butter",
        "8=FIX.4.1 37=2 38=2 40=milk",
        "8=FIX.4.1 37=3 38=3 40=bread"
    };
    private static final String[] FIX_RESPONSES = new String[] {
        "37=1 38=2 40=butter \r\n",
        "37=2 38=4 40=milk \r\n",
        "37=3 38=6 40=bread \r\n"
    };

    @Produce("direct:fix")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {
        mock.expectedMessageCount(FIX_RESPONSES.length);
        mock.expectedBodiesReceived(FIX_RESPONSES);

        for (String request : FIX_REQUESTS) {
            template.sendBody("direct:fix", request);
        }

        mock.assertIsSatisfied();
    }

    public static class ContextConfig extends RouteBuilder {
        @Override
        public void configure() {
            DataFormat bindy = new BindyKeyValuePairDataFormat(FixOrder.class);
            from("direct:fix")
                .unmarshal(bindy)
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        FixOrder order = exchange.getIn().getBody(FixOrder.class);
                        Object body = exchange.getIn().getBody();

                        if (order.getProduct().equals("butter")) {
                            order.setQuantity("2");
                            body = order;
                        } else if (order.getProduct().equals("milk")) {
                            order.setQuantity("4");
                            body = Collections.singletonMap(order.getClass().getName(), order);
                        } else if (order.getProduct().equals("bread")) {
                            order.setQuantity("6");
                            body = Collections.singletonList(Collections.singletonMap(order.getClass().getName(), order));
                        }

                        exchange.getIn().setBody(body);
                    }
                })
                .marshal(bindy)
                .to("mock:result");
        }
    }

    @Message(keyValuePairSeparator = "=", pairSeparator = " ", type = "FIX", version = "4.1")
    public static class FixOrder {
        @KeyValuePairField(tag = 37)
        private String id;
        @KeyValuePairField(tag = 40)
        private String product;
        @KeyValuePairField(tag = 38)
        private String quantity;

        public String getId() {
            return id;
        }

        public String getProduct() {
            return product;
        }

        public String getQuantity() {
            return quantity;
        }

        public void setQuantity(String quantity) {
            this.quantity = quantity;
        }
    }
}
