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
package org.apache.camel.component.xslt.saxon;

import java.io.File;
import java.io.InputStream;

import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SAXSourceLogBodyTest extends CamelTestSupport {

    @Test
    public void testSAXSource() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", new File("src/test/resources/xslt/staff/staff.xml"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").streamCaching()
                    // attach a SaxSource to body
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            byte[] data = exchange.getIn().getBody(byte[].class);
                            InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, data);
                            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                            exchange.getIn().setBody(new SAXSource(xmlReader, new InputSource(is)));
                        }
                    })
                    // The ${body} will toString the body and print it, so we
                    // need to enable stream caching
                    .log(LoggingLevel.WARN, "${body}").to("xslt-saxon:xslt/common/staff_template.xsl").to("log:result").to("mock:result");
            }
        };
    }
}
