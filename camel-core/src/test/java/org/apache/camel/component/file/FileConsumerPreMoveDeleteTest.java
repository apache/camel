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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 895965 $
 */
public class FileConsumerPreMoveDeleteTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/premove");
        super.setUp();
    }

    public void testPreMoveDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/premove", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesMockWaitTime();

        File pre = new File("target/premove/work/hello.txt").getAbsoluteFile();
        assertFalse("Pre move file should have been deleted", pre.exists());
    }

    public void testPreMoveDeleteSameFileTwice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Hello Again World");

        template.sendBodyAndHeader("file://target/premove", "Hello World", Exchange.FILE_NAME, "hello.txt");
        // give time for consumer to process this file before we drop the next file
        Thread.sleep(2000);
        template.sendBodyAndHeader("file://target/premove", "Hello Again World", Exchange.FILE_NAME, "hello.txt");
        // give time for consumer to process this file before we drop the next file

        assertMockEndpointsSatisfied();

        // and file should still be deleted
        Thread.sleep(1000);

        File pre = new File("target/premove/work/hello.txt").getAbsoluteFile();
        assertFalse("Pre move file should have been deleted", pre.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/premove?preMove=work&delete=true&idempotent=false")
                    .process(new MyPreMoveCheckerProcessor())
                    .to("mock:result");
            }
        };
    }

    public static class MyPreMoveCheckerProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            File pre = new File("target/premove/work/hello.txt").getAbsoluteFile();
            assertTrue("Pre move file should exist", pre.exists());
        }
    }
}
