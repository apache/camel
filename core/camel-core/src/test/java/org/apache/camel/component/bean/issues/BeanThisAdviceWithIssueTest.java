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
package org.apache.camel.component.bean.issues;

import org.apache.camel.BeanScope;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

public class BeanThisAdviceWithIssueTest extends ContextTestSupport {

    private static final String ROUTE_ID = "mytest";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void shouldFire() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:mytest").id(ROUTE_ID).bean(this, "hello", BeanScope.Prototype).to("log:out");
            }

            public void hello(final Exchange exchange) {
            }
        });

        RouteReifier.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:extract");
            }
        });

        context.start();

        // given
        final MockEndpoint myMock = getMockEndpoint("mock:extract");

        myMock.expectedMessageCount(1);

        // when
        template.sendBody("direct:mytest", "test");

        // then
        myMock.assertIsSatisfied();
    }
}
