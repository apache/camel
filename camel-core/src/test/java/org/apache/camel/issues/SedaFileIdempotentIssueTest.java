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
package org.apache.camel.issues;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;

/**
 *
 */
public class SedaFileIdempotentIssueTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);
    private FileIdempotentRepository repository = new FileIdempotentRepository();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/inbox");
        createDirectory("target/inbox");

        // create file without using Camel
        File file = new File("target/inbox/hello.txt");
        FileOutputStream fos = new FileOutputStream(file, true);
        fos.write("Hello World".getBytes());
        fos.flush();
        fos.close();

        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        repository.setFileStore(new File("target/repo.txt"));
        jndi.bind("repo", repository);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(RuntimeException.class).process(new ShutDown());

                from("file:target/inbox?idempotent=true&noop=true&idempotentRepository=#repo&initialDelay=0&delay=10")
                    .to("log:begin")
                    .inOut("seda:process");

                from("seda:process")
                    .throwException(new RuntimeException("Testing with exception"));
            }
        };
    }

    public void testRepo() throws Exception {
        boolean done = latch.await(10, TimeUnit.SECONDS);
        assertTrue("Should stop Camel", done);

        assertEquals("No file should be reported consumed", 0, repository.getCache().keySet().size());
    }

    protected class ShutDown implements Processor {

        @Override
        public void process(final Exchange exchange) throws Exception {
            // shutdown route
            Thread thread = new Thread() {
                @Override
                public void run() {
                    // shutdown camel
                    try {
                        log.info("Stopping Camel");
                        exchange.getContext().stop();
                        log.info("Stopped Camel complete");
                        latch.countDown();
                    } catch (Exception e) {
                        // ignore
                        e.printStackTrace();
                    }
                }
            };
            // start shutdown in a separate thread
            thread.start();
        }
    }

}
