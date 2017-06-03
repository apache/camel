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
package org.apache.camel.itest.jetty;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class JettyJmsShutdownInProgressTest extends AbstractJUnit4SpringContextTests {
    private static int port = AvailablePortFinder.getNextAvailable(20034);
    private static final String URL = "http://localhost:" + port + "/test";
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("JettyJmsShutdownInProgressTest.port", Integer.toString(port));
    }

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate template;

    @Test
    public void testShutdownInProgress() throws Exception {
        Future<String> reply1 = template.asyncRequestBody(URL, "World", String.class);
        Future<String> reply2 = template.asyncRequestBody(URL, "Camel", String.class);

        // shutdown camel while in progress, wait 2 sec so the first req has been received in Camel route
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    JettyJmsShutdownInProgressTest.this.camelContext.stop();
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        // wait a bit more before sending next
        Thread.sleep(5000);

        // this one should fail
        try {
            template.requestBody(URL, "Tiger", String.class);
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            HttpOperationFailedException hofe = (HttpOperationFailedException) e.getCause();
            Assert.assertEquals(503, hofe.getStatusCode());
        }

        // but the 2 first should still return valid replies
        Assert.assertEquals("Bye World", reply1.get(10, TimeUnit.SECONDS));
        Assert.assertEquals("Bye Camel", reply2.get(10, TimeUnit.SECONDS));
    }

}