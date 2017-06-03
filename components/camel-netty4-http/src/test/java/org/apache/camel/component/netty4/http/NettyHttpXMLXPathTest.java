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
package org.apache.camel.component.netty4.http;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpXMLXPathTest extends BaseNettyTest {

    @Test
    public void testHttpXML() throws Exception {
        String out = template.requestBody("netty4-http:http://localhost:{{port}}/foo", "<person><name>Claus</name></person>", String.class);
        assertEquals("<quote>Camel rocks</quote>", out);

        out = template.requestBody("netty4-http:http://localhost:{{port}}/foo", "<person><name>James</name></person>", String.class);
        assertEquals("<quote>Camel really rocks</quote>", out);

        out = template.requestBody("netty4-http:http://localhost:{{port}}/foo", "<person><name>Jonathan</name></person>", String.class);
        assertEquals("<quote>Try Camel now</quote>", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/foo")
                    .choice()
                        .when().xpath("/person/name = 'Claus'")
                            .transform(constant("<quote>Camel rocks</quote>"))
                        .when().xpath("/person/name = 'James'")
                            .transform(constant("<quote>Camel really rocks</quote>"))
                        .otherwise()
                            .transform(constant("<quote>Try Camel now</quote>"));
            }
        };
    }

}
