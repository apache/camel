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
package org.apache.camel.component.directvm;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class DirectVmNoPropertyPropagationTest extends ContextTestSupport {

    @Test
    public void testPropertiesPropagatedOrNot() throws Exception {
        template.sendBody("direct-vm:start.noprops", "Hello World");
        template.sendBody("direct-vm:start.props", "Hello World");
        template.sendBody("direct-vm:start.default", "Hello World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // Starters.
                from("direct-vm:start.noprops").setProperty("abc", constant("def")).to("direct-vm:foo.noprops?propagateProperties=false&block=false");

                from("direct-vm:start.props").setProperty("abc", constant("def")).to("direct-vm:foo.props?propagateProperties=true");

                from("direct-vm:start.default").setProperty("abc", constant("def")).to("direct-vm:foo.props");

                // Asserters.
                from("direct-vm:foo.noprops").process(exchange -> assertNull(exchange.getProperty("abc")));

                from("direct-vm:foo.props").process(exchange -> assertEquals("def", exchange.getProperty("abc", String.class)));
            }
        };
    }

}
