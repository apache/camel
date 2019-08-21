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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for consuming from an absolute path
 */
public class FileConsumerAbsolutePathTest extends ContextTestSupport {

    private String base;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/reports");
        // use current dir as base as absolute path
        base = new File("").getAbsolutePath() + "/target/data/reports";
        super.setUp();
    }

    @Test
    public void testConsumeFromAbsolutePath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/data/reports", "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://" + base + "?initialDelay=0&delay=10&delete=true").convertBodyTo(String.class).to("mock:report");
            }
        };
    }
}
