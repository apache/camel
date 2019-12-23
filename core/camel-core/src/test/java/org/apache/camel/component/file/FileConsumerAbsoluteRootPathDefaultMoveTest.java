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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for consuming from an absolute path at root
 */
public class FileConsumerAbsoluteRootPathDefaultMoveTest extends ContextTestSupport {

    // run this test manually

    private String base = "/tmp/mytemp";

    @Test
    public void testDummy() {
        // noop
    }

    public void xxxTestConsumeFromAbsolutePath() throws Exception {
        deleteDirectory(base);

        context.getRouteController().startRoute("foo");

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Paris");
        mock.expectedFileExists(base + "/.camel/paris.txt");

        template.sendBodyAndHeader("file:" + base, "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file:" + base + "?initialDelay=0&delay=10").routeId("foo").noAutoStartup().convertBodyTo(String.class).to("mock:report");
            }
        };
    }
}
