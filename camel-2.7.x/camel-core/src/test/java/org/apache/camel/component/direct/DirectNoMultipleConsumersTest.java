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
package org.apache.camel.component.direct;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * MultipleConsumers option test.
 */
public class DirectNoMultipleConsumersTest extends TestSupport {

    public void testNoMultipleConsumersTest() throws Exception {
        CamelContext container = new DefaultCamelContext();
        container.disableJMX();

        container.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("mock:result");

                from("direct:in").to("mock:result");
            }
        });

        try {
            container.start();
            fail("Should have thrown an FailedToStartRouteException");
        } catch (FailedToStartRouteException e) {
            // expected
        } finally {
            container.stop();
        }
    }
}