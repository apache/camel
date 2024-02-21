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
package org.apache.camel.language.datasonnet;

import java.util.Collections;
import java.util.List;

import com.datasonnet.document.MediaTypes;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionsInJavaTest extends CamelTestSupport {
    @EndpointInject("mock:direct:response")
    protected MockEndpoint endEndpoint;

    @Produce("direct:expressionsInJava")
    protected ProducerTemplate expressionsInJavaProducer;

    @Produce("direct:fluentBuilder")
    protected ProducerTemplate fluentBuilderProducer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:expressionsInJava")
                                .choice()
                                    .when(datasonnet("payload == 'World'"))
                                        .setBody(datasonnet("'Hello, ' + payload", String.class))
                                    .otherwise()
                                        .setBody(datasonnet("'Good bye, ' + payload", String.class))
                                    .end()
                                .to("mock:direct:response");

                from("direct:fluentBuilder")
                        // no optional params, look in header
                        .setHeader(Exchange.CONTENT_TYPE, constant(MediaTypes.APPLICATION_JAVA_VALUE))
                        .setBody(DatasonnetExpression.builder("payload"))
                        .removeHeader(Exchange.CONTENT_TYPE)

                        // override output
                        .transform(DatasonnetExpression.builder("payload", String.class)
                                .outputMediaType(MediaTypes.APPLICATION_JSON))

                        // override input
                        .transform(
                                DatasonnetExpression.builder("payload", List.class).bodyMediaType(MediaTypes.APPLICATION_JSON))

                        // override both
                        .setHeader(Exchange.CONTENT_TYPE, constant(MediaTypes.APPLICATION_JSON_VALUE))
                        .setBody(constant("<root>some-value</root>"))
                        .transform(DatasonnetExpression.builder("payload.root['$']", String.class)
                                .bodyMediaType(MediaTypes.APPLICATION_XML)
                                .outputMediaType(MediaTypes.APPLICATION_JSON))
                        .to("mock:direct:response");
            }
        };
    }

    @Test
    public void testExpressionLanguageInJava() {
        endEndpoint.expectedMessageCount(1);
        expressionsInJavaProducer.sendBody("World");
        Exchange exchange = endEndpoint.assertExchangeReceived(endEndpoint.getReceivedCounter() - 1);
        String response = exchange.getIn().getBody().toString();
        assertEquals("Hello, World", response);
    }

    @Test
    public void testFluentBuilder() {
        endEndpoint.expectedMessageCount(1);
        fluentBuilderProducer.sendBody(Collections.singletonList("datasonnet"));
        Exchange exchange = endEndpoint.assertExchangeReceived(endEndpoint.getReceivedCounter() - 1);
        String response = exchange.getMessage().getBody(String.class);
        assertEquals("\"some-value\"", response);
    }
}
