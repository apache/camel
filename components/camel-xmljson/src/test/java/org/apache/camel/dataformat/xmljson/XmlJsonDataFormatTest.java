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

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Document;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;

/**
 * Basic tests for the XML JSON data format
 */
public class XmlJsonDataFormatTest extends AbstractJsonTestSupport {

    @Test
    public void testMarshalAndUnmarshal() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(1);
        mockJSON.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/json");
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockXML.expectedMessageCount(1);
        mockXML.expectedHeaderReceived(Exchange.CONTENT_TYPE, "application/xml");
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
    public void testUnmarshalJSONObject() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);
        JSON json = JSONSerializer.toJSON(in);

        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshal", json);
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("The XML document has an unexpected root node", "o", document.getDocumentElement().getLocalName());

        mockXML.assertIsSatisfied();
    }

    @Test
    public void testMarshalXMLSources() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.xml");
        DOMSource inDOM = context.getTypeConverter().convertTo(DOMSource.class, inStream);
        inStream = getClass().getResourceAsStream("testMessage1.xml");
        SAXSource inSAX = context.getTypeConverter().convertTo(SAXSource.class, inStream);
        inStream = getClass().getResourceAsStream("testMessage1.xml");
        Document inDocument = context.getTypeConverter().convertTo(Document.class, inStream);

        // save the expected body of the message to set it later
        Object expectedBody = template.requestBody("direct:marshal", inDOM);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        // reset the mock endpoint to get rid of the previous message
        mockJSON.reset();
        // all three messages should arrive, should be of type byte[] and
        // identical to one another
        mockJSON.expectedMessageCount(3);
        mockJSON.allMessages().body().isInstanceOf(byte[].class);
        mockJSON.expectedBodiesReceived(Arrays.asList(expectedBody, expectedBody, expectedBody));

        // start bombarding the route
        Object json = template.requestBody("direct:marshal", inDOM);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSONObject doesn't contain 7 keys", 7, obj.entrySet().size());
        template.requestBody("direct:marshal", inSAX);
        template.requestBody("direct:marshal", inDocument);

        mockJSON.assertIsSatisfied();
    }
    
    @Test
    public void testMarshalAndUnmarshalInline() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:jsonInline");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        MockEndpoint mockXML = getMockEndpoint("mock:xmlInline");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object json = template.requestBody("direct:marshalInline", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSONObject doesn't contain 7 keys", 7, obj.entrySet().size());

        template.sendBody("direct:unmarshalInline", jsonString);

        mockJSON.assertIsSatisfied();
        mockXML.assertIsSatisfied();
    }
    
    @Test
    public void testNamespacesDroppedInlineWithOptions() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage2-namespaces.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:jsonInlineOptions");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshalInlineOptions", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSON must contain 1 top-level element", 1, obj.entrySet().size());
        assertTrue("Top-level element must be named root", obj.has("root"));
        // check that no child of the top-level element has a colon in its key,
        // which would denote that
        // a namespace prefix exists
        for (Object key : obj.getJSONObject("root").keySet()) {
            assertFalse("A key contains a colon", ((String) key).contains(":"));
        }

        mockJSON.assertIsSatisfied();
    }
    
    @Test
    public void testUnmarshalToXMLInlineOptions() throws Exception {
        InputStream inStream = getClass().getResourceAsStream("testMessage1.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockXML = getMockEndpoint("mock:xmlInlineOptions");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshalInlineOptions", in);
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("The XML document doesn't carry newRoot as the root name", "newRoot", document.getDocumentElement().getLocalName());
        assertEquals("The number of direct child elements of newRoot with tag d (expandable property) is not 3", 3, document.getDocumentElement().getElementsByTagName("d").getLength());
        assertEquals("The number of direct child elements of newRoot with tag e (expandable property) is not 3", 3, document.getDocumentElement().getElementsByTagName("e").getLength());
        mockXML.assertIsSatisfied();
    }

    @Test
    public void testJsonArraysToXml() throws Exception {
        MockEndpoint mockXML = getMockEndpoint("mock:xmlInlineOptionsArray");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshalInlineOptionsArray", "[1, 2, 3, 4]");
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("There should be exactly 4 XML elements with tag 'el' (each array element)", 4, document.getDocumentElement().getElementsByTagName("el").getLength());
        assertEquals("The document root should be named 'ar' (the array root)", "ar", document.getDocumentElement().getLocalName());
        mockXML.assertIsSatisfied();
    }

    @Test
    public void testXmlArraysToJson() throws Exception {
        MockEndpoint mockJSON = getMockEndpoint("mock:jsonInlineOptionsArray");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshalInlineOptionsArray", "<ar><el>1</el><el>2</el><el>3</el><el>4</el></ar>");
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONArray array = (JSONArray) JSONSerializer.toJSON(jsonString);
        assertTrue("Expected a JSON array with string elements: 1, 2, 3, 4", array.containsAll(Arrays.asList("1", "2", "3", "4")));
        mockJSON.assertIsSatisfied();
    }
    
    @Test
    public void testEmptyBodyToJson() throws Exception {
        MockEndpoint mockJSON = getMockEndpoint("mock:null2xml");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isNull();

        template.requestBody("direct:unmarshalNull2Xml", "");
        mockJSON.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XmlJsonDataFormat format = new XmlJsonDataFormat();

                // from XML to JSON
                from("direct:marshal").marshal(format).to("mock:json");
                // from JSON to XML
                from("direct:unmarshal").unmarshal(format).to("mock:xml");
                
                // test null body to xml
                from("direct:unmarshalNull2Xml").unmarshal(format).to("mock:null2xml");

                // from XML to JSON - inline dataformat
                from("direct:marshalInline").marshal().xmljson().to("mock:jsonInline");
                // from JSON to XML - inline dataformat
                from("direct:unmarshalInline").unmarshal().xmljson().to("mock:xmlInline");
                
                Map<String, String> xmlJsonOptions = new HashMap<String, String>();
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.ENCODING, "UTF-8");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.FORCE_TOP_LEVEL_OBJECT, "true");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.TRIM_SPACES, "true");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.ROOT_NAME, "newRoot");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.SKIP_NAMESPACES, "true");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.REMOVE_NAMESPACE_PREFIXES, "true");
                xmlJsonOptions.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.EXPANDABLE_PROPERTIES, "d e");

                // from XML to JSON - inline dataformat w/ options
                from("direct:marshalInlineOptions").marshal().xmljson(xmlJsonOptions).to("mock:jsonInlineOptions");
                // from JSON to XML - inline dataformat w/ options
                from("direct:unmarshalInlineOptions").unmarshal().xmljson(xmlJsonOptions).to("mock:xmlInlineOptions");

                Map<String, String> xmlJsonOptionsArrays = new HashMap<String, String>();
                xmlJsonOptionsArrays.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.ELEMENT_NAME, "el");
                xmlJsonOptionsArrays.put(org.apache.camel.model.dataformat.XmlJsonDataFormat.ARRAY_NAME, "ar");

                // from XML arrays to JSON - inline dataformat w/ options
                from("direct:marshalInlineOptionsArray").marshal().xmljson(xmlJsonOptionsArrays).to("mock:jsonInlineOptionsArray");
                // from JSON arrays to XML - inline dataformat w/ options
                from("direct:unmarshalInlineOptionsArray").unmarshal().xmljson(xmlJsonOptionsArrays).to("mock:xmlInlineOptionsArray");


            }
        };
    }

}
