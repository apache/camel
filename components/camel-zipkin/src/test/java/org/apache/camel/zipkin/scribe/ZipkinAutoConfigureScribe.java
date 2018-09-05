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

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.zipkin.ZipkinTracer;
import org.junit.Test;

/**
 * Integration test requires running Zipkin/Scribe running
 *
 * <p>The easiest way to run is locally:
 * <pre>{@code
 * curl -sSL https://zipkin.io/quickstart.sh | bash -s
 * SCRIBE_ENABLED=true java -jar zipkin.jar
 * }</pre>
 *
 * <p>This test has to be run with environment variables set. For example:
 * <pre>{@code
 * ZIPKIN_COLLECTOR_THRIFT_SERVICE_HOST=localhost
 * ZIPKIN_COLLECTOR_THRIFT_SERVICE_PORT=9410
 * }</pre>
 */
public class ZipkinAutoConfigureScribe extends CamelTestSupport {

    private ZipkinTracer zipkin;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        zipkin = new ZipkinTracer();
        // we have one route as service
        zipkin.addClientServiceMapping("seda:cat", "cat");
        zipkin.addServerServiceMapping("seda:cat", "cat");
        // should auto configure as we have not setup a span reporter

        // attaching ourself to CamelContext
        zipkin.init(context);

        return context;
    }

    @Test
    public void testZipkinRoute() throws Exception {
        template.requestBody("direct:start", "Hello Cat");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:cat");

                from("seda:cat").routeId("cat")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(1000,2000)}"));
            }
        };
    }
}
