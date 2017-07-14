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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FileContentBasedRouterTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/cbr");
        super.setUp();
    }

    private void sendFiles() {
        template.sendBodyAndHeader("file://target/cbr", "Hello London", "CamelFileName", "london.txt");
        template.sendBodyAndHeader("file://target/cbr", "Hello Paris", "CamelFileName", "paris.txt");
        template.sendBodyAndHeader("file://target/cbr", "Hello Copenhagen", "CamelFileName", "copenhagen.txt");
    }

    public void testRouteLondon() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        // should not load the content of the body into memory unless demand for it
        // so the type received should be a GenericFile (holder for the file)
        mock.message(0).body().isInstanceOf(GenericFile.class);

        sendFiles();

        assertMockEndpointsSatisfied();
    }

    public void testRouteParis() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:paris");
        mock.expectedMessageCount(1);
        // should not load the content of the body into memory unless demand for it
        // so the type received should be a GenericFile (holder for the file)
        mock.message(0).body().isInstanceOf(GenericFile.class);

        sendFiles();

        assertMockEndpointsSatisfied();
    }

    public void testRouteOther() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("CamelFileName", "copenhagen.txt");
        // should not load the content of the body into memory unless demand for it
        // so the type received should be a GenericFile (holder for the file)
        mock.message(0).body().isInstanceOf(GenericFile.class);

        sendFiles();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/cbr?noop=true&initialDelay=0&delay=10")
                    .choice()
                        .when(header("CamelFileName").isEqualTo("london.txt")).to("mock:london")
                        .when(header("CamelFileName").isEqualTo("paris.txt")).to("mock:paris")
                        .otherwise().to("mock:other");
            }
        };
    }
}
