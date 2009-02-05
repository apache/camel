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
package org.apache.camel.spring.file;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.file.FileComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * @version $Revision$
 */
@ContextConfiguration
public class SpringFileAntPathMatcherFileFilterTest extends AbstractJUnit38SpringContextTests {
    protected String expectedBody = "Godday World";
    @Autowired
    protected ProducerTemplate template;
    @EndpointInject(name = "myFileEndpoint")
    protected Endpoint inputFile;
    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    public void testAntPatchMatherFilter() throws Exception {
        result.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(inputFile, "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(inputFile, "Bye World", FileComponent.HEADER_FILE_NAME, "bye.xml");
        template.sendBodyAndHeader(inputFile, expectedBody, FileComponent.HEADER_FILE_NAME, "subfolder/foo/godday.txt");
        template.sendBodyAndHeader(inputFile, "Bad world", FileComponent.HEADER_FILE_NAME, "subfolder/badday.txt");
        template.sendBodyAndHeader(inputFile, "Day world", FileComponent.HEADER_FILE_NAME, "day.xml");

        result.assertIsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        TestSupport.deleteDirectory("target/antpathmatcher");
        super.setUp();
    }
}