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
package org.apache.camel.component.stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class StreamRouteBuilderTest extends ContextTestSupport {

    public void testStringContent() {
        template.sendBody("direct:start", "<content/>");
    }

    public void testBinaryContent() {
        template.sendBody("direct:start", new byte[] {1, 2, 3, 4});
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setHeader("stream", constant(System.out))
                    .to("stream:err", "stream:out", "stream:file?file=/tmp/foo", "stream:header");
            }
        };
    }
    
}
