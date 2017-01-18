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
        from("foo:wine?period=3000")
            .to("wine:Wine?amount=3")
            .log("Wine ordered ${body}");

        from("foo:bar?period=5000")
            .to("bar:GinTonic?amount=5")
            .log("Bar ordered ${body}");
    }
}
