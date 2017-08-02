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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.IdempotentRepository;

/**
 * Unit test for the idempotentRepositoryRef option.
 */
public class FileConsumerIdempotentRefTest extends ContextTestSupport {

    private static volatile boolean invoked;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRepo", new MyIdempotentRepository());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/idempotent");
        super.setUp();
        template.sendBodyAndHeader("file://target/idempotent/", "Hello World", Exchange.FILE_NAME, "report.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/idempotent/?idempotent=true&idempotentRepository=#myRepo&move=done/${file:name}&initialDelay=0&delay=10")
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    public void testIdempotentRef() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);

        // move file back
        File file = new File("target/idempotent/done/report.txt");
        File renamed = new File("target/idempotent/report.txt");
        file.renameTo(renamed);

        // should NOT consume the file again, let a bit time go
        Thread.sleep(100);
        assertMockEndpointsSatisfied();

        assertTrue("MyIdempotentRepository should have been invoked", invoked);
    }

    public class MyIdempotentRepository implements IdempotentRepository<String> {

        public boolean add(String messageId) {
            // will return true 1st time, and false 2nd time
            boolean result = invoked;
            invoked = true;
            assertEquals("report.txt", messageId);
            return !result;
        }

        public boolean contains(String key) {
            return invoked;
        }

        public boolean remove(String key) {
            return true;
        }

        public boolean confirm(String key) {
            return true;
        }
        
        @Override
        public void clear() {
            return;  
        }

        public void start() throws Exception {
        }

        public void stop() throws Exception {
        }
    }
    
}