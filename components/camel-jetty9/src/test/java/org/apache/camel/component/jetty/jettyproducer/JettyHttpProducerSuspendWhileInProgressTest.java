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
package org.apache.camel.component.jetty.jettyproducer;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
@Ignore
public class JettyHttpProducerSuspendWhileInProgressTest extends BaseJettyTest {

    private String serverUri = "jetty://http://localhost:" + getPort() + "/cool";

    @Test
    public void testJettySuspendWhileInProgress() throws Exception {
        // these tests does not run well on AIX or Windows
        if (isPlatform("aix") || isPlatform("windows")) {
            return;
        }

        context.getShutdownStrategy().setTimeout(50);

        // send a request/reply and have future handle so we can shutdown while in progress
        Future<String> reply = template.asyncRequestBodyAndHeader(serverUri, null, "name", "Camel", String.class);

        // shutdown camel while in progress, wait 2 sec so the first req has been received in Camel route
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    context.stop();
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        // wait a bit more before sending next
        Thread.sleep(5000);

        // now send a new req/reply
        Future<String> reply2 = template.asyncRequestBodyAndHeader(serverUri, null, "name", "Tiger", String.class);

        // the first should wait to have its reply returned
        assertEquals("Bye Camel", reply.get(20, TimeUnit.SECONDS));

        // the 2nd should have a 503 returned as we are shutting down
        try {
            reply2.get(20, TimeUnit.SECONDS);
            fail("Should throw exception");
        } catch (Exception e) {
            RuntimeCamelException rce = assertIsInstanceOf(RuntimeCamelException.class, e.getCause());
            HttpOperationFailedException hofe = assertIsInstanceOf(HttpOperationFailedException.class, rce.getCause());
            assertEquals(503, hofe.getStatusCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(serverUri)
                    .log("Got data will wait 10 sec with reply")
                    .delay(10000)
                    .transform(simple("Bye ${header.name}"));
            }
        };
    }
}