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
package org.foo;

import org.apache.camel.builder.RouteBuilder;

/**
 * Camel route that uses the foo, bar and wine connectors
 */
public class FooBarWineRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("foo:ThirstyBear?period=2000")
            .log("Who is this: ${header.whoami}")
            .to("wine:Wine?amount=2")
            .log("ThirstyBear ordered ${body}");

        from("foo:Moes?period=5000")
            .log("Who is this: ${header.whoami}")
            .to("bar:Beer?amount=5&celebrity=true")
            .log("Moes ordered ${body}");
    }
}
