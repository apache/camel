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
package org.apache.camel.component.flatpack;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test to verify that splitRows=true option works with XML Conversion.
 */
@ContextConfiguration
public class XMLSplitRowsTest extends AbstractJUnit4SpringContextTests {
    private static final Logger LOG = LoggerFactory.getLogger(XMLSplitRowsTest.class);

    @EndpointInject("mock:results")
    protected MockEndpoint results;

    protected String[] expectedFirstName = {"JOHN", "JIMMY", "JANE", "FRED"};

    @Test
    public void testHeaderAndTrailer() throws Exception {
        results.expectedMessageCount(6);
        results.assertIsSatisfied();

        int counter = 0;
        List<Exchange> list = results.getReceivedExchanges();

        // assert header
        Element header = list.get(0).getIn().getBody(Document.class).getDocumentElement();
        NodeList headerNodes = header.getElementsByTagName("Column");
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element column = (Element)headerNodes.item(i);
            if (column.getAttribute("name").equals("INDICATOR")) {
                assertEquals("HBT", column.getTextContent());
            } else if (column.getAttribute("name").equals("DATE")) {
                assertEquals("20080817", column.getTextContent());
            } else {
                fail("Invalid Header Field");
            }
        }

        // assert body
        for (Exchange exchange : list.subList(1, 5)) {
            Message in = exchange.getIn();
            Element record = in.getBody(Document.class).getDocumentElement();
            NodeList columnNodes = record.getElementsByTagName("Column");
            boolean firstNameFound = false;
            for (int i = 0; i < columnNodes.getLength(); i++) {
                Element column = (Element)columnNodes.item(i);
                if (column.getAttribute("name").equals("FIRSTNAME")) {
                    assertEquals(expectedFirstName[counter], column.getTextContent());
                    firstNameFound = true;
                }
            }
            assertTrue(firstNameFound);
            LOG.info("Result: " + counter + " = " + record);
            counter++;
        }

        // assert trailer
        Element trailer = list.get(5).getIn().getBody(Document.class).getDocumentElement();
        NodeList trailerNodes = trailer.getElementsByTagName("Column");
        for (int i = 0; i < trailerNodes.getLength(); i++) {
            Element column = (Element)trailerNodes.item(i);
            if (column.getAttribute("name").equals("INDICATOR")) {
                assertEquals("FBT", column.getTextContent());
            } else if (column.getAttribute("name").equals("STATUS")) {
                assertEquals("SUCCESS", column.getTextContent());
            } else {
                fail("Invalid Trailer Field");
            }
        }
    }
}
