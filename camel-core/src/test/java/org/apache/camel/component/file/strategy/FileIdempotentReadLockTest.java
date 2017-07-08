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
package org.apache.camel.component.file.strategy;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

/**
 * @version
 */
public class FileIdempotentReadLockTest extends ContextTestSupport {

    MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/changed/");
        createDirectory("target/changed/in");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    public void testIdempotentReadLock() throws Exception {
        assertEquals(0, myRepo.getCacheSize());

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(2).create();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("file:target/changed/in", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/changed/in", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        assertTrue(notify.matches(5, TimeUnit.SECONDS));

        // the files are kept on commit
        // if you want to remove them then the idempotent repo need some way to evict idle keys
        assertEquals(2, myRepo.getCacheSize());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/changed/in?initialDelay=0&delay=10&readLock=idempotent&idempotentRepository=#myRepo")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // we are in progress
                            int size = myRepo.getCacheSize();
                            assertTrue(size == 1 || size == 2);
                        }
                    })
                    .to("mock:result");
            }
        };
    }
}
