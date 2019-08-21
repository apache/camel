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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for the how FileProducer behaves a bit strangely when generating
 * filenames
 */
public class FilerProducerFileNamesTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/reports");
        super.setUp();
    }

    // START SNIPPET: e1
    @Test
    public void testProducerWithMessageIdAsFileName() throws Exception {
        Endpoint endpoint = context.getEndpoint("direct:report");
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody("This is a good report");

        FileEndpoint fileEndpoint = resolveMandatoryEndpoint("file:target/data/reports/report.txt", FileEndpoint.class);
        String id = fileEndpoint.getGeneratedFileName(exchange.getIn());

        template.send("direct:report", exchange);

        File file = new File("target/data/reports/" + id);
        assertEquals("File should exists", true, file.exists());
    }

    @Test
    public void testProducerWithHeaderFileName() throws Exception {
        template.sendBody("direct:report2", "This is super good report");
        File file = new File("target/report-super.txt");
        assertEquals("File should exists", true, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:report").to("file:target/data/reports/");

                from("direct:report2").setHeader(Exchange.FILE_NAME, constant("report-super.txt")).to("file:target/");
            }
        };
    }
    // END SNIPPET: e1

}
