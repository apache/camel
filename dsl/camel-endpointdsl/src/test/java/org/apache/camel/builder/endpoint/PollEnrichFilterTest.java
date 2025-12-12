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
package org.apache.camel.builder.endpoint;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;

public class PollEnrichFilterTest extends BaseEndpointDslTest {

    private static final String TEST_DATA_DIR = BaseEndpointDslTest.generateUniquePath(PollEnrichFilterTest.class);

    @Override
    public void doPreSetup() {
        deleteDirectory(TEST_DATA_DIR);
    }

    @Test
    public void testPollEnrichFile() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isEqualTo("Hello World");

        template.sendBodyAndHeader("file://" + TEST_DATA_DIR, "Bye World", Exchange.FILE_NAME, "unknown.txt");
        template.sendBodyAndHeader("file://" + TEST_DATA_DIR, "Hello World", Exchange.FILE_NAME, "myfile.txt");

        template.sendBody("direct:start", "Start");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() {
                from(direct("start"))
                        .pollEnrich(file(TEST_DATA_DIR).noop(true).filter(new GenericFileFilter<File>() {
                            @Override
                            public boolean accept(GenericFile<File> file) {
                                return file.getFileName().startsWith("my");
                            }
                        }), 1000)
                        .to(mock("result"));
            }
        };
    }

}
