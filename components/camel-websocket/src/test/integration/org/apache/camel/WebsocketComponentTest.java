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
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class WebsocketComponentTest extends CamelTestSupport {

    @Test
    public void testWebsocketCall() throws Exception {
        Thread.sleep(15 * 60 * 1000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                from("websocket://foo").log("received message: ").log(" --> ${body}").choice().when(body(Integer.class).isGreaterThan(500)).to("seda://greater500")
                        .when(body(Integer.class).isLessThan(500)).to("seda://less500").otherwise().setBody(constant("request failed...")).to("websocket://foo");

                from("seda://greater500").setBody(constant("forms/c.xml")).log("C --> ${body}").to("websocket://foo");

                from("seda://less500").setBody(constant("forms/b.xml")).log("B --> ${body}").to("websocket://foo");
            }
        };
    }
}
