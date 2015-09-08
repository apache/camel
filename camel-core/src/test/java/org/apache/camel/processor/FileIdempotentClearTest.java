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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;

/**
 * @version 
 */
public class FileIdempotentClearTest extends ContextTestSupport {

    private File store = new File("target/idempotentfilestore.dat");
    private IdempotentRepository<String> repo;

    @Override
    protected void setUp() throws Exception {
        // delete file store before testing
        if (store.exists()) {
            store.delete();
        }
        repo = FileIdempotentRepository.fileIdempotentRepository(store);

        super.setUp();
    }

    public void testClear() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Foo", "Bar");

        template.sendBodyAndHeader("direct:start", "Foo", "messageId", "A");
        template.sendBodyAndHeader("direct:start", "Camel rocks", "messageId", "A");
        template.sendBodyAndHeader("direct:start", "Bar", "messageId", "B");

        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("Camel rocks");

        repo.clear();
        
        assertFalse(repo.contains("A"));
        assertFalse(repo.contains("B"));
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
