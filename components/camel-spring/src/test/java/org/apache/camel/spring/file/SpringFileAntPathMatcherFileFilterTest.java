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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringRunWithTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class SpringFileAntPathMatcherFileFilterTest extends SpringRunWithTestSupport {
    protected String expectedBody = "Godday World";
    @Autowired
    protected ProducerTemplate template;
    @EndpointInject("ref:myFileEndpoint")
    protected Endpoint inputFile;
    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Test
    public void testAntPatchMatherFilter() throws Exception {
        result.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(inputFile, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(inputFile, "Bye World", Exchange.FILE_NAME, "bye.xml");
        template.sendBodyAndHeader(inputFile, expectedBody, Exchange.FILE_NAME, "subfolder/foo/godday.txt");
        template.sendBodyAndHeader(inputFile, "Bad world", Exchange.FILE_NAME, "subfolder/badday.txt");
        template.sendBodyAndHeader(inputFile, "Day world", Exchange.FILE_NAME, "day.xml");

        result.assertIsSatisfied();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/antpathmatcher");
        super.setUp();
    }
}
