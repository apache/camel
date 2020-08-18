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
package org.apache.camel.processor.intercept;

import org.apache.camel.builder.RouteBuilder;

public class InterceptFromWithStopRouteTest extends InterceptFromRouteTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                interceptFrom().filter(header("foo").isEqualTo("bar")).to("mock:b")
                        // need end to end filter
                        .end()
                        // stop continue routing no matter what
                        .stop();

                from("direct:start").to("mock:a");
            }
        };
    }

    @Override
    protected void prepareMatchingTest() {
        a.expectedMessageCount(0);
        b.expectedMessageCount(1);
    }

    @Override
    protected void prepareNonMatchingTest() {
        a.expectedMessageCount(0);
        b.expectedMessageCount(0);
    }
}
