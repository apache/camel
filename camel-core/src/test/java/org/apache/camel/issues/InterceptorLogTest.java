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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Testing http://activemq.apache.org/camel/dsl.html
 */
public class InterceptorLogTest extends ContextTestSupport {

    public void testInterceptor() throws Exception {
        MockEndpoint intercept = getMockEndpoint("mock:intercept");
        intercept.expectedMessageCount(2);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        intercept.assertIsSatisfied();
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets log all steps in all routes (must use proceed to let the exchange continue its
                // normal route path instead of swallowing it here by our intercepter.
                intercept().to("log:foo").proceed().to("mock:intercept");
                intercept().to("log:bar").proceed();

                from("seda:foo").to("seda:bar");
                from("seda:bar").intercept().to("log:cheese").to("mock:result");
            }
        };
    }

}
