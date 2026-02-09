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
package org.apache.camel.component.netty.http;

import io.netty.util.ResourceLeakDetector;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class NettyEnricherLeakTest extends BaseNettyTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void leakNoTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

                from("netty-http:http://localhost:" + getPort() + "/test")
                        .transform().simple("${body}");

                from("direct:outer")
                        .to("netty-http:http://localhost:" + getPort() + "/test?disconnect=true");
            }
        });
        context.start();

        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        for (int i = 0; i < 10; ++i) {
            template.requestBody("direct:outer", "input", String.class);
        }
    }

    @Test
    public void leakTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

                from("netty-http:http://localhost:" + getPort() + "/test")
                        .transform().simple("${body}");

                from("direct:outer")
                        .enrich("netty-http:http://localhost:" + getPort() + "/test?disconnect=true",
                                AggregationStrategies.string(), false, false);
            }
        });
        context.start();

        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        for (int i = 0; i < 10; ++i) {
            template.requestBody("direct:outer", "input", String.class);
        }
    }
}
