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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test that we can produce files even for InOut MEP.
 */
public class FileMEPInOutTest extends ContextTestSupport {

    public void testMEPInOutTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        // request is InOut
        template.requestBodyAndHeader("direct:in", "Hello World", FileComponent.HEADER_FILE_NAME,
            "FileMEPInOutTest.txt");

        // give Camel time to create the file
        Thread.sleep(1000);

        File file = new File("target/FileMEPInOutTest.txt");
        file = file.getAbsoluteFile();
        assertEquals("Hello World", IOConverter.toString(file));

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in")
                    .to("file://target/?append=false")
                    .to("mock:result");
            }
        };
    }

}