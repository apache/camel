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
package org.apache.camel.testng.patterns;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.testng.CamelTestSupport;
import org.testng.annotations.Test;

public class IsUseAdviceWithTest extends CamelTestSupport {

    @Test
    public void testIsUseAdviceWith() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // replace the from with seda:foo
                replaceFromWith("seda:foo");
            }
        });
        // we must manually start when we are done with all the advice with
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    public boolean isUseAdviceWith() {
        // tell we are using advice with, which allows us to advice the route
        // before Camel is being started, and thus can replace activemq with something else.
        return true;
    }

    // This is the route we want to test
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we do not have activemq on the classpath
                // but the route has it included
                from("activemq:queue:foo")
                    .to("mock:result");
            }
        };
    }

}
