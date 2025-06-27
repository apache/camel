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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ThreadsIdTest extends ContextTestSupport {

    @Test
    public void testThreadsId() throws Exception {
        Assertions.assertInstanceOf(SetBodyProcessor.class, context.getProcessor("Setting body"));
        Assertions.assertInstanceOf(ThreadsProcessor.class, context.getProcessor("Parallel processing"));
        Assertions.assertInstanceOf(LogProcessor.class, context.getProcessor("After threads"));
        Assertions.assertInstanceOf(SendProcessor.class, context.getProcessor("End of route"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute")
                        .setBody().simple("Hello Camel from ${routeId}").id("Setting body")
                        .threads(5).id("Parallel processing")
                        .log("${body}").id("After threads")
                        .to("mock:end").id("End of route");
            }
        };
    }
}
