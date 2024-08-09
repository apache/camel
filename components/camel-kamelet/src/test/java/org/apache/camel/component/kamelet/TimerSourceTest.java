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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.support.DefaultKameletResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimerSourceTest extends CamelTestSupport {

    @Test
    public void testKamelet() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, "text/plain");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void shouldResolveKamelet() throws Exception {
        ModelCamelContext camelContext = new DefaultCamelContext();
        Assertions.assertEquals(0, camelContext.getRouteTemplateDefinitions().size());
        new DefaultKameletResolver().resolve("timer-source", camelContext);
        Assertions.assertEquals(1, camelContext.getRouteTemplateDefinitions().size());

        Assertions.assertEquals(3, camelContext.getRouteTemplateDefinitions().get(0).getTemplateParameters().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kamelet:timer-source?message=Hello&period=1000").to("mock:result");

            }
        };
    }
}
