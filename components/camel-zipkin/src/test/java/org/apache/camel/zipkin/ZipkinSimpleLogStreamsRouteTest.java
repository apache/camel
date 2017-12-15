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
package org.apache.camel.zipkin;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import zipkin2.reporter.Reporter;

public class ZipkinSimpleLogStreamsRouteTest extends CamelTestSupport {

    private ZipkinTracer zipkin;

    protected void setSpanReporter(ZipkinTracer zipkin) {
        zipkin.setSpanReporter(Reporter.NOOP);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        zipkin = new ZipkinTracer();
        zipkin.setServiceName("dude");
        zipkin.setIncludeMessageBodyStreams(true);
        setSpanReporter(zipkin);

        // attaching ourself to CamelContext
        zipkin.init(context);

        return context;
    }

    @Test
    public void testZipkinRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:dude", "Hello World");
        }

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:dude").routeId("dude")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));
            }
        };
    }
}
