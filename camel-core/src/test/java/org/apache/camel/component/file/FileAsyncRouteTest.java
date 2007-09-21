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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FileAsyncRouteTest extends ContextTestSupport {
    protected Object expectedBody = "Hello there!";
    protected String uri = "file:target/test-async-inbox?delete=true";

    CountDownLatch receivedLatch = new CountDownLatch(1);
    CountDownLatch processingLatch = new CountDownLatch(1);
    AtomicReference<File> file = new AtomicReference<File>();

    @Override
    protected void tearDown() throws Exception {
        processingLatch.countDown();
        receivedLatch.countDown();
        super.tearDown();
    }
    
    public void testFileRoute() throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(expectedBody);
        result.setDefaulResultWaitMillis(5000);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);

        // Wait till the exchange is delivered to the processor
        assertTrue("Async processor received exchange", receivedLatch.await(5, TimeUnit.SECONDS));
        File file = this.file.get();
        
        // The file consumer support async processing of the exchange,
        // so the file should not get deleted until the exchange
        // finishes being asynchronously processed.
        Thread.sleep(1000);
        assertTrue("File should exist", file.exists());

        // Release the async processing thread so that the exchange completes
        // and the file
        // gets deleted.
        processingLatch.countDown();
        Thread.sleep(1000);
        assertFalse("File should not exist", file.exists());

        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).thread(1).to("direct:a");
                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        file.set((File)exchange.getIn().getBody());
                        // Simulate a processing delay..
                        receivedLatch.countDown();
                        processingLatch.await();
                    }
                }).to("mock:result");
            }
        };
    }
}
