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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.AdviceWith.adviceWith;

public class AdviceWithTryCatchFinallyTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAdviceTryCatchFinally() throws Exception {
        context.addRoutes(createRouteBuilder());

        adviceWith(context, "my-route", a -> a.weaveById("replace-me")
                .replace().to("mock:replaced"));

        context.start();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("my-route")
                    .doTry()
                        .log("try")
                        .to("mock:replace-me").id("replace-me")
                    .doCatch(Exception.class)
                        .log("catch")
                    .doFinally()
                        .log("finally")
                    .end();
            }
        };
    }
}
