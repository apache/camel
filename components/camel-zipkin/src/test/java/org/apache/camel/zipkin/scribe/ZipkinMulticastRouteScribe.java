/**
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
package org.apache.camel.zipkin.scribe;

import java.util.concurrent.TimeUnit;

import com.github.kristofa.brave.scribe.ScribeSpanCollector;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.zipkin.ZipkinTracer;
import org.junit.Test;

/**
 * Integration test requires running Zipkin/Scribe running
 *
 * The easiest way is to run using zipkin-docker: https://github.com/openzipkin/docker-zipkin
 *
 * Adjust the IP address to what IP docker-machines have assigned, you can use
 * <tt>docker-machines ls</tt>
 */
public class ZipkinMulticastRouteScribe extends CamelTestSupport {

    private String ip = "192.168.99.100";
    private ZipkinTracer zipkin;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        zipkin = new ZipkinTracer();

        zipkin.addClientServiceMapping("seda:a", "a");
        zipkin.addClientServiceMapping("seda:b", "b");
        zipkin.addClientServiceMapping("seda:c", "c");
        zipkin.addServerServiceMapping("seda:a", "a");
        zipkin.addServerServiceMapping("seda:b", "b");
        zipkin.addServerServiceMapping("seda:c", "c");
        zipkin.setSpanCollector(new ScribeSpanCollector(ip, 9410));

        // attaching ourself to CamelContext
        zipkin.init(context);

        return context;
    }

    @Test
    public void testZipkinRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        template.requestBody("direct:start", "Hello World");

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:a").routeId("start");

                from("seda:a").routeId("a")
                    .log("routing at ${routeId}")
                    .multicast()
                        .to("seda:b")
                        .to("seda:c")
                    .end()
                    .log("End of routing");

                from("seda:b").routeId("b")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));

                from("seda:c").routeId("c")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(0,100)}"));
            }
        };
    }
}
