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
package org.apache.camel.language.juel.issues;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.StringSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.language.juel.JuelExpression.el;

/**
 * @version 
 */
public class XsltLosesHeaderTest extends CamelTestSupport {
    protected String xslt = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
            + "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
            + "<xsl:template match=\"/cats\">"
            + "<b>dummy</b>"
            + "</xsl:template>"
            + "</xsl:stylesheet>";

    @Test
    public void testXsltLosesHeader() throws Exception {

        MockEndpoint endpointAfter = getMockEndpoint("mock:After");
        MockEndpoint endpointBefore = getMockEndpoint("mock:Before");

        endpointBefore.expectedBodiesReceived("header:hello");
        endpointAfter.expectedBodiesReceived("header:hello");

        template.send("seda:xslttest", new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody("dummy");
            }
        });

        endpointBefore.assertIsSatisfied();
        endpointAfter.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws TransformerConfigurationException {
                from("seda:xslttest")
                        .setHeader("testheader", el("hello"))
                        .setBody(el("header:${in.headers.testheader}"))
                        .to("mock:Before")
                        .setBody(el("<cats><cat id=\"1\"/><cat id=\"2\"/></cats>"))
                        .process(XsltBuilder.xslt(new StringSource(xslt)))
                        .setBody(el("header:${in.headers.testheader}"))
                        .to("mock:After");
            }
        };
    }
}