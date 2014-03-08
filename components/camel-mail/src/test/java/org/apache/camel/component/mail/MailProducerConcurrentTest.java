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
package org.apache.camel.component.mail;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Mail producer concurrent test.
 *
 * @version 
 */
public class MailProducerConcurrentTest extends CamelTestSupport {

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        Mailbox.clearAll();

        NotifyBuilder builder = new NotifyBuilder(context).whenDone(files).create();

        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());

        final CountDownLatch latch = new CountDownLatch(files);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader("direct:start", "Message " + index, "To", "someone@localhost");
                    latch.countDown();
                    return null;
                }
            });
        }

        // wait first for all the exchanges above to be thoroughly sent asynchronously
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied();
        assertTrue(builder.matchesMockWaitTime());

        Mailbox box = Mailbox.get("someone@localhost");
        assertEquals(files, box.size());

        // as we use concurrent producers the mails can arrive out of order
        Set<Object> bodies = new HashSet<Object>();
        for (int i = 0; i < files; i++) {
            bodies.add(box.get(i).getContent());
        }

        assertEquals("There should be " + files + " unique mails", files, bodies.size());
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("smtp://camel@localhost", "mock:result");
            }
        };
    }

}
