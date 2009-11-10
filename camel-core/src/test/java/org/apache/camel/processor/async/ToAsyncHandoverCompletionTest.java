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
package org.apache.camel.processor.async;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.language.simple.SimpleLanguage.simple;

/**
 * @version $Revision$
 */
public class ToAsyncHandoverCompletionTest extends ContextTestSupport {

    public void testToAsyncHandoverCompletion() throws Exception {
        deleteDirectory("target/toasync");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedFileExists("target/toasync/done/hello.txt");

        template.sendBodyAndHeader("file://target/toasync", "World", Exchange.FILE_NAME, "hello.txt");

        Thread.sleep(1000);

        // now there is a delay of 3 seconds but the original file should still be there as its in progress
        File target = new File("target/toasync/hello.txt").getAbsoluteFile();
        assertEquals(true, target.exists());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/toasync?move=done").to("mock:a").toAsync("direct:bar", 5).to("mock:result");

                from("direct:bar").delay(3000).transform(simple("Bye ${body}"));
            }
        };
    }
}