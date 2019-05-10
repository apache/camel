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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ZipDeflaterDataFormatFileDeleteTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/zip");
        super.setUp();
    }

    @Test
    public void testZipFileDelete() throws Exception {
        NotifyBuilder oneExchangeDone = new NotifyBuilder(context).whenDone(1).create();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/data/zip", "Hello World", Exchange.FILE_NAME, "hello.txt");
        assertMockEndpointsSatisfied();

        // wait till the exchange is done which means the file should then have been deleted
        oneExchangeDone.matchesWaitTime();

        File in = new File("target/data/zip/hello.txt");
        Assert.assertFalse("Should have been deleted " + in, in.exists());

        File out = new File("target/data/zip/out/hello.txt.zip");
        Assert.assertTrue("Should have been created " + out, out.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/zip?initialDelay=0&delay=10&delete=true")
                    .marshal().zipDeflater()
                    .to("file:target/data/zip/out?fileName=${file:name}.zip")
                    .to("mock:result");
            }
        };
    }
}