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

package org.apache.camel.component.restlet;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class RestRestletRouterIdTest extends CamelTestSupport {

    @BindToRegistry("setId")
    private static final Processor SET_ROUTE_ID_AS_BODY = exchange -> exchange.getIn().setBody(exchange.getFromRouteId());
    private int port = AvailablePortFinder.getNextAvailable();

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                restConfiguration().component("restlet").host("localhost").port(port).bindingMode(RestBindingMode.auto);

                rest("/app").get("/test1").id("test1").to("direct:setId").get("/test2").route().routeId("test2").to("direct:setId");
                rest("/app").get("/test4").route().routeId("test4").to("direct:setId2");

                from("direct:setId").process(SET_ROUTE_ID_AS_BODY);

                from("direct:setId2").process("setId");

                rest("/app").get("/test3").route().id("test3").process(SET_ROUTE_ID_AS_BODY);

                rest("/app").get("/REST-TEST").route().routeId("TEST").id("REST-TEST").to("direct:setId");

                rest("/app").get("/test5").route().id("test5").to("direct:setId").endRest().to("direct:setId2");
            };
        };
    }

    @Test
    public void test() throws Exception {

        Assert.assertEquals("\"test1\"", template.requestBodyAndHeader("http://localhost:" + port + "/app/test1", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));

        Assert.assertEquals("\"test2\"", template.requestBodyAndHeader("http://localhost:" + port + "/app/test2", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));

        Assert.assertEquals("\"test3\"", template.requestBodyAndHeader("http://localhost:" + port + "/app/test3", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));

        Assert.assertEquals("\"test4\"", template.requestBodyAndHeader("http://localhost:" + port + "/app/test4", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));

        Assert.assertEquals("\"test5\"", template.requestBodyAndHeader("http://localhost:" + port + "/app/test5", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));

        Assert.assertEquals("\"REST-TEST\"",
                            template.requestBodyAndHeader("http://localhost:" + port + "/app/REST-TEST", "", Exchange.HTTP_METHOD, HttpMethods.GET, String.class));
    }
}
