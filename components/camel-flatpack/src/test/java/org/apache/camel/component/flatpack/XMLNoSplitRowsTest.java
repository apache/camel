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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test to verify that splitRows=false option works with XML Conversion.
 */
@CamelSpringTest
@ContextConfiguration
public class XMLNoSplitRowsTest {

    private static final Logger LOG = LoggerFactory.getLogger(XMLNoSplitRowsTest.class);

    @EndpointInject("mock:results")
    protected MockEndpoint results;

    protected String[] expectedFirstName = { "JOHN", "JIMMY", "JANE", "FRED" };

    @Test
    public void testHeaderAndTrailer() throws Exception {
        results.expectedMessageCount(1);
        results.message(0).body().isInstanceOf(Document.class);
        results.message(0).header("camelFlatpackCounter").isEqualTo(6);

        results.assertIsSatisfied();

        Document data = results.getExchanges().get(0).getIn().getBody(Document.class);
        Element docElement = data.getDocumentElement();
        assertEquals("Dataset", docElement.getTagName());

        // assert header
        Element header = (Element) docElement.getElementsByTagName("DatasetHeader").item(0);
        NodeList headerNodes = header.getElementsByTagName("Column");
        for (int i = 0; i < headerNodes.getLength(); i++) {
            Element column = (Element) headerNodes.item(i);
            if (column.getAttribute("name").equals("INDICATOR")) {
                assertEquals("HBT", column.getTextContent());
            } else if (column.getAttribute("name").equals("DATE")) {
                assertEquals("20080817", column.getTextContent());
            } else {
                fail("Invalid Header Field");
            }
        }

        // assert body
        NodeList list = docElement.getElementsByTagName("DatasetRecord");
        for (int counter = 0; counter < list.getLength(); counter++) {
            Element record = (Element) list.item(counter);
            NodeList columnNodes = record.getElementsByTagName("Column");
            boolean firstNameFound = false;
            for (int i = 0; i < columnNodes.getLength(); i++) {
                Element column = (Element) columnNodes.item(i);
                if (column.getAttribute("name").equals("FIRSTNAME")) {
                    assertEquals(expectedFirstName[counter], column.getTextContent());
                    firstNameFound = true;
                }
            }
            assertTrue(firstNameFound);
            LOG.info("Result: {} = {}", counter, record);
        }

        // assert trailer
        Element trailer = (Element) docElement.getElementsByTagName("DatasetTrailer").item(0);
        NodeList trailerNodes = trailer.getElementsByTagName("Column");
        for (int i = 0; i < trailerNodes.getLength(); i++) {
            Element column = (Element) trailerNodes.item(i);
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
