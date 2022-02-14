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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTemplatedRouteTest {

    @Test
    void testMain() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
                templatedRoute("myTemplate")
                        .routeId("my-route")
                        .parameter("foo", "fooVal")
                        .parameter("bar", "barVal");
            }
        });

        main.start();

        CamelContext context = main.getCamelContext();
        assertEquals(1, context.adapt(ModelCamelContext.class).getRouteDefinitions().size());
        assertEquals("my-route", context.adapt(ModelCamelContext.class).getRouteDefinitions().get(0).getId());

        MockEndpoint mock = context.getEndpoint("mock:barVal", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello Camel");

        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("direct:fooVal", "Hello Camel");

        mock.assertIsSatisfied();

        main.stop();
    }

}
