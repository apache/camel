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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.junit.jupiter.api.Test;

public class SupervisingRouteControllerSplitOnExceptionTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(25);
        src.setBackOffMaxAttempts(3);
        src.setInitialDelay(100);
        src.setThreadPoolSize(1);

        return context;
    }

    @Test
    public void testSupervising() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:uk").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("direct:start", "<hello>World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException().handled(true).split().method(SupervisingRouteControllerSplitOnExceptionTest.class, "mySplit")
                        .streaming().log("Exception occurred").to("mock:error");

                from("direct:start")
                        .choice()
                        .when(xpath("/person/city = 'London'"))
                        .log("UK message")
                        .to("mock:uk")
                        .otherwise()
                        .log("Other message")
                        .to("mock:other");
            }
        };
    }

    public static List<Message> mySplit(@Body Message inputMessage) {
        List<Message> outputMessages = new ArrayList<>();

        Message outputMessage = inputMessage.copy();
        outputMessage.setBody(inputMessage.getBody());
        outputMessages.add(outputMessage);

        return outputMessages;
    }

}
