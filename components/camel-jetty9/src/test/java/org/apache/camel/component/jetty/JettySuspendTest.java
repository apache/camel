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
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

/**
 * @version 
 */
public class JettySuspendTest extends BaseJettyTest {

    private String serverUri = "http://localhost:" + getPort() + "/cool";

    @Test
    public void testJettySuspend() throws Exception {
        context.getShutdownStrategy().setTimeout(50);

        String reply = template.requestBody(serverUri, "World", String.class);
        assertEquals("Bye World", reply);

        // now suspend jetty
        HttpConsumer consumer = (HttpConsumer) context.getRoute("route1").getConsumer();
        assertNotNull(consumer);

        // suspend
        consumer.suspend();

        try {
            template.requestBody(serverUri, "Moon", String.class);
            fail("Should throw exception");
        } catch (Exception e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(503, cause.getStatusCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty://" + serverUri).id("route1")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
