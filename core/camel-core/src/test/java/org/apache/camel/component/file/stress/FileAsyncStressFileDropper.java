/*
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
package org.apache.camel.component.file.stress;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;

@Ignore("Manual test")
public class FileAsyncStressFileDropper extends ContextTestSupport {

    private static int counter;

    public static String getFilename() {
        return "" + counter++ + ".txt";
    }

    @Override
    public void setUp() throws Exception {
        // do not test on windows
        if (isPlatform("windows")) {
            return;
        }

        super.setUp();
        deleteDirectory("target/data/filestress");
    }

    public void testDropInNewFiles() throws Exception {
        // do not test on windows
        if (isPlatform("windows")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(250);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // generate a new file continuously
                from("timer:foo?period=50").setHeader(Exchange.FILE_NAME, method(FileAsyncStressFileDropper.class, "getFilename")).setBody(constant("Hello World"))
                    .to("file:target/data/filestress").to("mock:result");
            }
        };
    }

}
