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
package org.apache.camel.component.bean;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ProxyReturnFutureTest extends ContextTestSupport {

    // START SNIPPET: e2
    public void testFutureEcho() throws Exception {
        Echo service = ProxyHelper.createProxy(context.getEndpoint("direct:echo"), Echo.class);

        Future<String> future = service.asText(4);
        log.info("Got future");

        log.info("Waiting for future to be done ...");
        String reply = future.get(5, TimeUnit.SECONDS);
        assertEquals("Four", reply);
    }
    // END SNIPPET: e2

    public void testFutureEchoCallTwoTimes() throws Exception {
        Echo service = ProxyHelper.createProxy(context.getEndpoint("direct:echo"), Echo.class);

        Future<String> future = service.asText(4);
        log.info("Got future");

        log.info("Waiting for future to be done ...");
        assertEquals("Four", future.get(5, TimeUnit.SECONDS));

        future = service.asText(5);
        log.info("Got future");

        log.info("Waiting for future to be done ...");
        assertEquals("Four", future.get(5, TimeUnit.SECONDS));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:echo")
                    .delay(250)
                    .transform().constant("Four");
            }
        };
    }

    // START SNIPPET: e1
    public interface Echo {

        // returning a Future indicate asynchronous invocation
        Future<String> asText(int number);

    }
    // END SNIPPET: e1

}
