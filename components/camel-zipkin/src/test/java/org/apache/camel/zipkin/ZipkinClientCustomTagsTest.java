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
package org.apache.camel.zipkin;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import zipkin2.reporter.Reporter;

public class ZipkinClientCustomTagsTest extends CamelTestSupport {

    private ZipkinTracer zipkin;

    protected void setSpanReporter(ZipkinTracer zipkin) {
        zipkin.setSpanReporter(Reporter.NOOP);
    }
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        zipkin = new ZipkinTracer();
        // we have 2 routes as services
        zipkin.addClientServiceMapping("seda:a", "a");
        zipkin.addServerServiceMapping("seda:a", "a");
        setSpanReporter(zipkin);

        // attaching ourself to CamelContext
        zipkin.init(context);
        return context;
    }
    @Test
    public void testZipkinRoute() throws Exception {
        template.requestBody("direct:start", "Camel client sends custom tags");
    }
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:a");

                from("seda:a").routeId("a")
                        .setBody().constant("Camel client sends custom tags")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                Map<String, String> customTags = new HashMap<>();
                                customTags.put("customTag1", "value1");
                                customTags.put("customTag2", "value2");
                                exchange.setProperty("camel.client.customtags", customTags);
                            }
                        });
            }
        };
    }
}
