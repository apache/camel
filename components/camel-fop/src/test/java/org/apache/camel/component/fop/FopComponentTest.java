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
package org.apache.camel.component.fop;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Before;
import org.junit.Test;

public class FopComponentTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data");

        super.setUp();
    }

    @Test
    public void createPdfUsingXmlDataAndXsltTransformation() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        FileInputStream inputStream = new FileInputStream("src/test/data/xml/data.xml");

        template.sendBody(inputStream);
        resultEndpoint.assertIsSatisfied();

        PDDocument document = PDDocument.load(new File("target/data/result.pdf"));
        String pdfText = FopHelper.extractTextFrom(document);
        assertTrue(pdfText.contains("Project"));    //from xsl template
        assertTrue(pdfText.contains("John Doe"));   //from data xml

        // assert on the header "foo" being populated
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        assertEquals("Header value is lost!", "bar", exchange.getIn().getHeader("foo"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("xslt:xslt/template.xsl")
                    .setHeader("foo", constant("bar"))
                    .to("fop:pdf")
                    .setHeader(Exchange.FILE_NAME, constant("result.pdf"))
                    .to("file:target/data")
                    .to("mock:result");
            }
        };
    }
}
