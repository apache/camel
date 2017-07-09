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
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Simple unit test to consume a new file
 */
public class NewFileConsumeTest extends ContextTestSupport {

    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/consumefile");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testNewFileConsumer() throws Exception {
        FileComponent comp = new FileComponent();
        comp.setCamelContext(context);

        // create a file to consume
        createDirectory("target/consumefile");
        FileOutputStream fos = new FileOutputStream(new File("target/consumefile/hello.txt"));
        try {
            fos.write("Hello World".getBytes());
        } finally {
            fos.close();
        }

        Endpoint endpoint = comp.createEndpoint("file://target/consumefile", "target/consumefile", new HashMap<String, Object>());
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                assertNotNull(exchange);
                String body = exchange.getIn().getBody(String.class);
                assertEquals("Hello World", body);
                latch.countDown();
            }
        });
        consumer.start();
        latch.await(5, TimeUnit.SECONDS);

        consumer.stop();
    }

}