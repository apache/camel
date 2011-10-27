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

import junit.framework.Assert;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.HttpOperationFailedException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class JettyJmsShutdownInProgressTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate template;

    @Test
    public void testShutdownInProgress() throws Exception {
        Future<String> reply1 = template.asyncRequestBody("http://localhost:9002/test", "World", String.class);
        Future<String> reply2 = template.asyncRequestBody("http://localhost:9002/test", "Camel", String.class);

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
            template.requestBody("http://localhost:9002/test", "Tiger", String.class);
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