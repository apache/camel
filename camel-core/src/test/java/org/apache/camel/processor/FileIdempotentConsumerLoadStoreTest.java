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
package org.apache.camel.processor;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;

/**
 * @version 
 */
public class FileIdempotentConsumerLoadStoreTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    private File store = new File("target/idempotentfilestore.dat");
    private IdempotentRepository<String> repo;

    public void testLoadStore() throws Exception {
        assertFalse(repo.contains("1"));
        assertFalse(repo.contains("2"));
        assertFalse(repo.contains("3"));
        assertTrue(repo.contains("4"));

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("4", "four");
        sendMessage("1", "one");
        sendMessage("3", "three");

        resultEndpoint.assertIsSatisfied();

        assertTrue(repo.contains("1"));
        assertTrue(repo.contains("2"));
        assertTrue(repo.contains("3"));
        assertTrue(repo.contains("4"));
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    protected void setUp() throws Exception {
        // delete file store before testing
        if (store.exists()) {
            store.delete();
        }

        // insert existing to store
        FileOutputStream fos = new FileOutputStream(store);
        fos.write("4\n".getBytes());
        fos.close();

        repo = FileIdempotentRepository.fileIdempotentRepository(store);

        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .idempotentConsumer(header("messageId"), repo)
                    .to("mock:result");
            }
        };
    }
}