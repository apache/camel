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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit test to test CAMEL-1652
 */
@Ignore("Manual test")
public class FileBatchConsumerMemoryLeakTest extends ContextTestSupport {

    private String fileUrl = "target/data/filesorter/";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filesorter");
        super.setUp();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDummy() {
        // need a single test method to not fail because of no test methods
    }

    /**
     * Process 100 files with a sorted file endpoint. For each exchange the body will be replaced
     * by a large buffer. In reality a similar thing happens if you have a lot of large files
     * and use convertBodyTo(String.class). In both cases the Exchanges becomes quite large.
     * The test will consume a lot of memory if all exchanges are kept in a list while doing
     * the batch processing. This is because the garbage collector can not clean them as they
     * are referenced in the list of exchanges.
     * <p/>
     * The test is not really a good integration test as it simply waits and does not fail
     * or succeed fast
     */
    @Test
    public void testMemoryLeak() throws Exception {
        // run this manually and browse the memory usage, eg in IDEA there is a Statistics tab

        deleteDirectory("target/data/filesorter/archiv");
        for (int c = 0; c < 100; c++) {
            template.sendBodyAndHeader(fileUrl + "c", "test", Exchange.FILE_NAME, c + ".dat");
        }
        context.addRoutes(new EndpointRouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(file(fileUrl + "/c/").sortBy("ignoreCase:file:name"))
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                StringBuilder buf = new StringBuilder(10000000);
                                buf.setLength(1000000);
                                exchange.getIn().setBody(buf.toString());
                            }
                        }).to(file("target/data/filesorter/archiv"));
            }
        });
        context.start();

        Thread.sleep(30 * 1000L);
    }

}
