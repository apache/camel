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
package org.apache.camel.converter.jaxb;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.Bar;
import org.apache.camel.example.PurchaseOrder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The partClass of the jaxb data format configured on the context (such as with camel.dataformat.jaxb.part-class in
 * Camel Main) does not apply to the REST DSL binding, where the type of the verb decides the body type.
 */
public class JaxbRestBindingPartClassTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("direct-rest", new DirectRestConsumerFactory());
        JaxbDataFormat jaxb = (JaxbDataFormat) context.resolveDataFormat("jaxb");
        jaxb.setPartClass(Bar.class);
        return context;
    }

    @Test
    void restBindingDoesNotTakeThePartClass() throws Exception {
        MockEndpoint posted = getMockEndpoint("mock:posted");
        posted.expectedMessageCount(1);

        template.requestBody("direct:post-order", "<purchaseOrder name=\"foo\" price=\"1.23\" amount=\"2.0\"/>");

        posted.assertIsSatisfied();
        assertInstanceOf(PurchaseOrder.class, posted.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("direct-rest").bindingMode(RestBindingMode.xml);

                rest("/order").post().type(PurchaseOrder.class).to("mock:posted");
            }
        };
    }

    /**
     * Runs the REST services on direct endpoints, named after the verb and the path.
     */
    private static final class DirectRestConsumerFactory implements RestConsumerFactory {

        @Override
        public Consumer createConsumer(
                CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
                throws Exception {
            String uri = "direct:" + verb + basePath.replace('/', '-');
            return camelContext.getEndpoint(uri).createConsumer(processor);
        }
    }
}
