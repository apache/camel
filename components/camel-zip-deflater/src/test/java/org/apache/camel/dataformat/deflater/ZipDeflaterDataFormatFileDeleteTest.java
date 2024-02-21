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
package org.apache.camel.dataformat.deflater;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipDeflaterDataFormatFileDeleteTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/zip");
        super.setUp();
    }

    @Test
    public void testZipFileDelete() throws Exception {
        NotifyBuilder oneExchangeDone = new NotifyBuilder(context).whenDone(1).create();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/data/zip", "Hello World", Exchange.FILE_NAME, "hello.txt");
        MockEndpoint.assertIsSatisfied(context);

        // wait till the exchange is done which means the file should then have been deleted
        oneExchangeDone.matchesWaitTime();

        File in = new File("target/data/zip/hello.txt");
        assertFalse(in.exists(), "Should have been deleted " + in);

        File out = new File("target/data/zip/out/hello.txt.zip");
        assertTrue(out.exists(), "Should have been created " + out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/data/zip?initialDelay=0&delay=10&delete=true")
                        .marshal().zipDeflater()
                        .to("file:target/data/zip/out?fileName=${file:name}.zip")
                        .to("mock:result");
            }
        };
    }
}
