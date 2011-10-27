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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.Test;

public class HttpProxyRouteTest extends BaseJettyTest {

    private int size = 500;

    @Test
    public void testHttpProxy() throws Exception {
        log.info("Sending " + size + " messages to a http endpoint which is proxied/bridged");

        StopWatch watch = new StopWatch();
        for (int i = 0; i < size; i++) {
            String out = template.requestBody("http://localhost:{{port}}/hello?foo=" + i, null, String.class);
            assertEquals("Bye " + i, out);
        }

        log.info("Time taken: " + TimeUtils.printDuration(watch.taken()));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jetty://http://localhost:{{port}}/hello")
                    .to("http://localhost:{{port}}/bye?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("jetty://http://localhost:{{port}}/bye").transform(header("foo").prepend("Bye "));
            }
        };
    }    

}
