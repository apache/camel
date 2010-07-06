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

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.RoutePolicySupport;

/**
 * @version $Revision$
 */
public class FileConsumerSuspendAndResumeTest extends ContextTestSupport {

    private MyPolicy myPolicy = new MyPolicy();

    public void testConsumeSuspendAndResumeFile() throws Exception {
        deleteDirectory("target/suspended");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("file://target/suspended", "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader("file://target/suspended", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        Thread.sleep(1000);

        // the route is suspended by the policy so we should only receive one
        String[] files = new File("target/suspended/").getAbsoluteFile().list();
        assertNotNull(files);
        assertEquals("The file should exists", 1, files.length);

        // reset mock
        mock.reset();
        mock.expectedBodiesReceived("Hello World");

        // now resume it
        myPolicy.resumeConsumer();

        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        // and the file is now deleted
        files = new File("target/suspended/").getAbsoluteFile().list();
        assertNotNull(files);
        assertEquals("The file should exists", 0, files.length);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/suspended?maxMessagesPerPoll=1&delete=true")
                    .routePolicy(myPolicy).id("myRoute")
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    private class MyPolicy extends RoutePolicySupport {

        private int counter;
        private Consumer consumer;

        public void onExchangeDone(Route route, Exchange exchange) {
            this.consumer = route.getConsumer();
            // only stop it at first run
            if (counter++ == 0) {
                try {
                    super.stopConsumer(consumer);
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }

        public void resumeConsumer() throws Exception {
            super.startConsumer(consumer);
        }
    }

}