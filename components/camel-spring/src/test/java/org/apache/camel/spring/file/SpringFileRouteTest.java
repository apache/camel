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
package org.apache.camel.spring.file;

import java.io.File;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class SpringFileRouteTest extends SpringRunWithTestSupport {
    protected String expectedBody = "Hello World!";
    @Autowired
    protected ProducerTemplate template;
    @Autowired
    protected Endpoint inputFile;
    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Test
    public void testMocksAreValid() throws Exception {
        // lets check that our injected endpoint is valid
        FileEndpoint fileEndpoint = assertIsInstanceOf(FileEndpoint.class, inputFile);
        assertEquals("File", new File("target/test-default-inbox"), fileEndpoint.getFile());

        result.expectedBodiesReceived(expectedBody);
        result.setResultWaitTime(5000);

        template.sendBodyAndHeader(inputFile, expectedBody, Exchange.FILE_NAME, "hello.txt");

        result.assertIsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/test-default-inbox");
        super.setUp();
    }
}
