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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Component;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.ShutdownRoute.Default;

/**
 * @version 
 */
public class ShutdownNotDeferTest extends ContextTestSupport {

    private static final AtomicBoolean CONSUMER_SUSPENDED = new AtomicBoolean();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/deferred");
        super.setUp();
    }

    public void testShutdownNotDeferred() throws Exception {
        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(1);

        template.sendBody("seda:foo", "A");
        template.sendBody("seda:foo", "B");
        template.sendBody("seda:foo", "C");
        template.sendBody("seda:foo", "D");
        template.sendBody("seda:foo", "E");

        assertMockEndpointsSatisfied();

        context.stop();

        assertTrue("Should have been suspended", CONSUMER_SUSPENDED.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo")
                    .startupOrder(1)
                    .to("file://target/deferred");

                // use file component to transfer files from route 1 -> route 2
                MyDeferFileEndpoint defer = new MyDeferFileEndpoint("file://target/deferred?initialDelay=0&delay=10", getContext().getComponent("file"));
                defer.setFile(new File("target/deferred"));

                from(defer)
                    // do NOT defer it but use default for testing this
                    .startupOrder(2).shutdownRoute(Default)
                    .to("mock:bar");
            }
        };
    }

    private static final class MyDeferFileEndpoint extends FileEndpoint {

        private MyDeferFileEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        protected FileConsumer newFileConsumer(Processor processor, GenericFileOperations<File> operations) {
            return new FileConsumer(this, processor, operations) {
                @Override
                protected void doSuspend() throws Exception {
                    CONSUMER_SUSPENDED.set(true);
                    super.doSuspend();
                }
            };
        }
    }
}