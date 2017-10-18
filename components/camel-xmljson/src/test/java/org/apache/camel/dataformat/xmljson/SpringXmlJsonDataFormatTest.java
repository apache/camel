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
package org.apache.camel.dataformat.xmljson;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.camel.StreamCache;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;

import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


/**
 * Test the Spring DSL
 */
public class SpringXmlJsonDataFormatTest extends CamelSpringTestSupport {

    @BeforeClass
    public static void checkXomInClasspath() {
        AbstractJsonTestSupport.checkXomInClasspath();
    }
    
    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object json = template.requestBody("direct:marshal", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSONObject doesn't contain 7 keys", 7, obj.entrySet().size());

        template.sendBody("direct:unmarshal", jsonString);

        mockJSON.assertIsSatisfied();
        mockXML.assertIsSatisfied();
    }
    
    @Test
    public void testEmptyBodyToJson() throws Exception {
        MockEndpoint mockJSON = getMockEndpoint("mock:emptyBody2Xml");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(StreamCache.class);

        StreamSource in = context.getTypeConverter().convertTo(StreamSource.class, new ByteArrayInputStream("".getBytes()));
        template.requestBody("direct:emptyBody2Unmarshal", in);
        mockJSON.assertIsSatisfied();
    }

    @Test
    public void testSomeOptionsToXML() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockXML = getMockEndpoint("mock:xmlWithOptions");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshalWithOptions", in);
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("The XML document doesn't carry newRoot as the root name", "newRoot", document.getDocumentElement().getLocalName());
        // with expandable properties, array elements are converted to XML as a
        // sequence of repetitive XML elements with the local name equal to the
        // JSON key
        // for example: { number: [1,2,3] }, normally converted to:
        // <number><e>1</e><e>2</e><e>3</e></number> (where e can be modified by
        // setting elementName)
        // would be converted to
        // <number>1</number><number>2</number><number>3</number>, if number is
        // set as an expandable property
        assertEquals("The number of direct child elements of newRoot with tag d (expandable property) is not 3", 3, document.getDocumentElement().getElementsByTagName("d").getLength());
        assertEquals("The number of direct child elements of newRoot with tag e (expandable property) is not 3", 3, document.getDocumentElement().getElementsByTagName("e").getLength());
        mockXML.assertIsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/xmljson/SpringXmlJsonDataFormatTest.xml");
    }

}
