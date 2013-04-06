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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ProxyReturnFutureExceptionTest extends ContextTestSupport {

    public void testFutureEchoException() throws Exception {
        Echo service = ProxyHelper.createProxy(context.getEndpoint("direct:echo"), Echo.class);

        Future<String> future = service.asText(4);
        log.info("Got future");

        log.info("Waiting for future to be done ...");
        try {
            assertEquals("Four", future.get(5, TimeUnit.SECONDS));
            fail("Should have thrown exception");
        } catch (ExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:echo")
                    .delay(250)
                    .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    public interface Echo {
        Future<String> asText(int number);
    }

}
