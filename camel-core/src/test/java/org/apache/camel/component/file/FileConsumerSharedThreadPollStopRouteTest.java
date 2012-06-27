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

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class FileConsumerSharedThreadPollStopRouteTest extends FileConsumerSharedThreadPollTest {

    public void testSharedThreadPool() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        // thread thread name should be the same
        mock.message(0).header("threadName").isEqualTo(mock.message(1).header("threadName"));

        template.sendBodyAndHeader("file:target/a", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/b", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        // now stop a
        context.stopRoute("a");

        resetMocks();
        mock.expectedBodiesReceived("Bye World 2");
        // a should not be polled
        mock.expectedFileExists("target/a/hello2.txt");

        template.sendBodyAndHeader("file:target/a", "Hello World 2", Exchange.FILE_NAME, "hello2.txt");
        template.sendBodyAndHeader("file:target/b", "Bye World 2", Exchange.FILE_NAME, "bye2.txt");

        assertMockEndpointsSatisfied();

        // now start a, which should pickup the file
        resetMocks();
        mock.expectedBodiesReceived("Hello World 2");
        context.startRoute("a");

        assertMockEndpointsSatisfied();
    }

}
