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
package org.apache.camel.dataformat.zipfile;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ZipSplitterRouteIssueTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/zip");
        super.setUp();
    }

    @Test
    public void testSplitter() throws Exception {
        getMockEndpoint("mock:entry").expectedMessageCount(2);

        template.sendBody("direct:decompressFiles", new File("src/test/resources/data.zip"));

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testSplitterWithWrongFile() throws Exception {
        getMockEndpoint("mock:entry").expectedMessageCount(0);
        getMockEndpoint("mock:errors").expectedMessageCount(1);

        //Send a file which is not exit
        template.sendBody("direct:decompressFiles", new File("src/test/resources/data"));
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:errors"));
                
                from("direct:decompressFiles")
                    .split(new ZipSplitter()).streaming().shareUnitOfWork()
                        .to("log:entry")
                        .to("mock:entry");
            }
        };
    }

}
