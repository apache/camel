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
package org.apache.camel.builder.xml;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.xslt.XsltBuilder.xslt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XsltOutputFileTest extends ContextTestSupport {

    @Test
    public void testXsltOutputFile() throws Exception {
        Files.createDirectories(testDirectory());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>");
        mock.expectedFileExists(testFile("xsltme.xml"));
        mock.message(0).body().isInstanceOf(File.class);

        template.sendBodyAndHeader("direct:start", "<hello>world!</hello>", Exchange.XSLT_FILE_NAME,
                testFile("xsltme.xml").toString());

        mock.assertIsSatisfied();
    }

    @Test
    public void testXsltOutputFileMissingHeader() {

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "<hello>world!</hello>"),
                "Should thrown exception");

        NoSuchHeaderException nshe = assertIsInstanceOf(NoSuchHeaderException.class, e.getCause());
        assertEquals(Exchange.XSLT_FILE_NAME, nshe.getHeaderName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                URL styleSheet = getClass().getResource("example.xsl");

                // output xslt as a file
                from("direct:start").process(xslt(styleSheet).outputFile()).to("mock:result");
            }
        };
    }
}
