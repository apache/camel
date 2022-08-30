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
package org.apache.camel.component.jms;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class JmsRequestReplyTemporaryCacheNoneTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNotAllowed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("activemq:queue:JmsRequestReplyTemporaryCacheNoneTest?replyToCacheLevelName=CACHE_NONE");

                from("activemq:queue:JmsRequestReplyTemporaryCacheNoneTest").process(exchange -> {
                    exchange.getIn().setBody("Bye World");
                    assertNotNull(exchange.getIn().getHeader("JMSReplyTo"));
                }).to("mock:result");
            }
        });
        context.start();

        try {
            template.requestBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals(
                    "ReplyToCacheLevelName cannot be CACHE_NONE when using temporary reply queues. The value must be either CACHE_CONSUMER, or CACHE_SESSION",
                    iae.getMessage());
        }
    }
}
