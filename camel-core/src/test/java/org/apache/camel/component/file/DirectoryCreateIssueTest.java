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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @author Albert Moraal
 * @version $Revision$
 */
public class DirectoryCreateIssueTest extends TestSupport {
    private CamelContext context;
    private ProducerTemplate template;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = new DefaultCamelContext();
        context.start();
        template = context.createProducerTemplate();
    }

    @Override
    protected void tearDown() throws Exception {
        context.stop();
        super.tearDown();
    }

    public void testFileCreatedAsDir() throws Exception {
        log.debug("testFileCreatedAsDir");
        final String path = "target/a/b/c/d/e/f/g/h";
        final int numFiles = 10;

        context.addRoutes(
            new RouteBuilder() {
                @Override
                public void configure() {
                    String[] destinations = new String[numFiles];
                    for (int i = 0; i < numFiles; i++) {
                        destinations[i] = "seda:file" + i;

                        from("seda:file" + i)
                            .setHeader(FileComponent.HEADER_FILE_NAME,
                                constant("file" + i + ".txt"))
                            .to("file://" + path + "/?append=false&noop=true");
                    }

                    from("seda:testFileCreatedAsDir")
                        .to(destinations);
                }
            }
        );

        deleteDirectory(new File("a"));

        template.send("seda:testFileCreatedAsDir", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("Contents of test file");
            }
        });

        // must sleep for some time to make sure runs on all platforms
        Thread.sleep(8 * 1000);

        for (int i = 0; i < numFiles; i++) {
            assertTrue((new File(path + "/file" + i + ".txt")).isFile());
        }
    }

}


