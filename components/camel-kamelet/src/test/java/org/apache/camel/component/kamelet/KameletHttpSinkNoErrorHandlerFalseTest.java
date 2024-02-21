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
package org.apache.camel.component.kamelet;

import java.net.UnknownHostException;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class KameletHttpSinkNoErrorHandlerFalseTest extends CamelTestSupport {

    @Test
    public void testHttpSink() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw exception");
        } catch (Exception e) {
            assertInstanceOf(UnknownHostException.class, e.getCause());
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myhttp")
                        .templateParameter("url")
                        .from("kamelet:source")
                        .removeHeaders("*")
                        .to("{{url}}");

                from("direct:start").routeId("test")
                        .errorHandler(deadLetterChannel("mock:dead"))
                        .kamelet("myhttp?noErrorHandler=false&url=https://webhook.unknownhost.sitessss/")
                        .log("${body}");
            }
        };
    }
}
