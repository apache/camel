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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteNodePrefixIdDuplicateTest extends ContextTestSupport {

    @Test
    public void testRoutePrefixId() throws Exception {
        Assertions.assertEquals(2, context.getRoutes().size());

        // ID should be prefixed
        SendProcessor send = context.getProcessor("myMock", SendProcessor.class);
        Assertions.assertNull(send);
        send = context.getProcessor("aaamyMock", SendProcessor.class);
        Assertions.assertNotNull(send);
        send = context.getProcessor("bbbmyMock", SendProcessor.class);
        Assertions.assertNotNull(send);

        // all nodes should include prefix
        Assertions.assertEquals(2, context.getRoute("foo").filter("aaa*").size());
        Assertions.assertEquals(2, context.getRoute("bar").filter("bbb*").size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo").nodePrefixId("aaa")
                        .to("mock:foo").id("myMock")
                        .to("seda:foo");

                from("direct:bar").nodePrefixId("bbb").routeId("bar")
                        .to("mock:bar").id("myMock")
                        .to("seda:bar");
            }
        };
    }
}
