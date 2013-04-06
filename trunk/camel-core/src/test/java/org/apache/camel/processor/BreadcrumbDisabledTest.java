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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class BreadcrumbDisabledTest extends MDCTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // MDC and breadcrumb disabled
                context.setUseMDCLogging(false);
                context.setUseBreadcrumb(false);

                from("direct:a").routeId("route-a")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertNull("Should not have breadcrumb", exchange.getIn().getHeader("breadcrumbId"));
                            }
                        })
                        .to("log:foo").to("direct:b");

                from("direct:b").routeId("route-b")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                assertNull("Should not have breadcrumb", exchange.getIn().getHeader("breadcrumbId"));
                            }
                        })
                        .to("log:bar").to("mock:result");
            }
        };
    }
}
