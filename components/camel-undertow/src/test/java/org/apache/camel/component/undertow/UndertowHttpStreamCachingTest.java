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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UndertowHttpStreamCachingTest extends BaseUndertowTest {

    private final String data = "abcdefg";

    @Test
    public void testTwoWayStreaming() {
        Exchange exchange = template.request("undertow:http://localhost:{{port}}/client", null);

        assertEquals(data, new String((byte[]) exchange.getMessage().getBody()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                getContext().setStreamCaching(true);
                getContext().getStreamCachingStrategy().setSpoolThreshold(3);

                from("undertow:http://localhost:{{port}}/client")
                        .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                        .to("http://localhost:{{port}}/server?bridgeEndpoint=true").to("log:lgName?showBody=true")
                        .end();
                from("undertow:http://localhost:{{port}}/server?httpMethodRestrict=POST").setBody(simple(data))
                        .to("log:lgName?showBody=true").end();
            }
        };
    }

}
