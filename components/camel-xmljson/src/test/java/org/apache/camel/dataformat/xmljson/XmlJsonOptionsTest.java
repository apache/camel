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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Document;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;

/**
 * Testing options to the XML JSON data format
 */
public class XmlJsonOptionsTest extends AbstractJsonTestSupport {

    @Test
    public void testSomeOptionsToJSON() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage1.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshal", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSON must contain 1 top-level element", 1, obj.entrySet().size());
        assertTrue("Top-level element must be named root", obj.has("root"));

        mockJSON.assertIsSatisfied();
    }

    @Test
    public void testXmlWithTypeAttributesToJSON() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage4.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshal", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("JSON must contain 1 top-level element", 1, obj.entrySet().size());
        assertTrue("Top-level element must be named root", obj.has("root"));

        mockJSON.assertIsSatisfied();
    }

    @Test
    public void testSomeOptionsToXML() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage1.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockXML = getMockEndpoint("mock:xml");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshal", in);
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

    @Test
    public void testNamespacesDropped() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage2-namespaces.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:json");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshal", in);
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
    public void testCustomNamespaceMappings() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage2-namespaces.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockXML = getMockEndpoint("mock:xmlNS");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshalNS", in);
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("Element surname must be qualified in the default namespace", "http://camel.apache.org/default", document.getDocumentElement().getElementsByTagName("surname").item(0)
                .getNamespaceURI());
        assertEquals("Root element must be qualified in the default namespace", "http://camel.apache.org/default", document.getDocumentElement().getNamespaceURI());
        assertEquals("Element surname must have namespace attributes", 2, document.getDocumentElement().getElementsByTagName("surname").item(0).getAttributes().getLength());
        assertEquals("Root element must have namespace attributes", 2, document.getDocumentElement().getAttributes().getLength());
        mockXML.assertIsSatisfied();
    }
    
    @Test
    public void testTypeHintsToJSON() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage5-typeHints.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:jsonTypeHints");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshalTypeHints", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("root.a must be number", Integer.valueOf(1), obj.getJSONObject("root").get("a"));
        assertEquals("root.b must be boolean", Boolean.TRUE, obj.getJSONObject("root").get("b"));

        mockJSON.assertIsSatisfied();
    }
    
    @Test
    public void testTypeHintsToXML() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage5-typeHints.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockXML = getMockEndpoint("mock:xmlTypeHints");
        mockXML.expectedMessageCount(1);
        mockXML.message(0).body().isInstanceOf(String.class);

        Object marshalled = template.requestBody("direct:unmarshalTypeHints", in);
        Document document = context.getTypeConverter().convertTo(Document.class, marshalled);
        assertEquals("Element a should exists", 1, document.getDocumentElement().getElementsByTagName("a").getLength());
        assertNotNull("Element a should have attribute type", document.getDocumentElement().getElementsByTagName("a").item(0).getAttributes().getNamedItem("type"));
        assertEquals("Element a should have attribute type with value number", "number",
                     document.getDocumentElement().getElementsByTagName("a").item(0).getAttributes().getNamedItem("type").getTextContent());
        assertEquals("Element b should have attribute type with value boolean", 
                     "boolean", document.getDocumentElement().getElementsByTagName("b").item(0).getAttributes().getNamedItem("type").getTextContent());
        mockXML.assertIsSatisfied();
    }
    
    @Test
    public void testPrefixedTypeHintsToJSON() throws Exception {
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/testMessage6-prefixedTypeHints.xml");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        MockEndpoint mockJSON = getMockEndpoint("mock:jsonPrefixedTypeHints");
        mockJSON.expectedMessageCount(1);
        mockJSON.message(0).body().isInstanceOf(byte[].class);

        Object json = template.requestBody("direct:marshalPrefixedTypeHints", in);
        String jsonString = context.getTypeConverter().convertTo(String.class, json);
        JSONObject obj = (JSONObject) JSONSerializer.toJSON(jsonString);
        assertEquals("root.a must be number", Integer.valueOf(1), obj.getJSONObject("root").get("a"));
        assertEquals("root.b must be boolean", Boolean.TRUE, obj.getJSONObject("root").get("b"));

        mockJSON.assertIsSatisfied();
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                XmlJsonDataFormat format = new XmlJsonDataFormat();
                format.setEncoding("UTF-8");
                format.setForceTopLevelObject(true);
                format.setTrimSpaces(true);
                format.setRootName("newRoot");
                format.setSkipNamespaces(true);
                format.setRemoveNamespacePrefixes(true);
                format.setExpandableProperties(Arrays.asList("d", "e"));

                // from XML to JSON
                from("direct:marshal").marshal(format).to("mock:json");
                // from JSON to XML
                from("direct:unmarshal").unmarshal(format).to("mock:xml");

                XmlJsonDataFormat namespacesFormat = new XmlJsonDataFormat();
                List<XmlJsonDataFormat.NamespacesPerElementMapping> namespaces = new ArrayList<XmlJsonDataFormat.NamespacesPerElementMapping>();
                namespaces.add(new XmlJsonDataFormat.NamespacesPerElementMapping("", "|ns1|http://camel.apache.org/test1||http://camel.apache.org/default|"));
                namespaces.add(new XmlJsonDataFormat.NamespacesPerElementMapping("surname", "|ns2|http://camel.apache.org/personalData|ns3|http://camel.apache.org/personalData2|"));
                namespacesFormat.setNamespaceMappings(namespaces);
                namespacesFormat.setTrimSpaces(true);
                // from XML to JSON
                from("direct:marshalNS").marshal(namespacesFormat).to("mock:jsonNS");
                // from JSON to XML
                from("direct:unmarshalNS").unmarshal(namespacesFormat).to("mock:xmlNS");

                XmlJsonDataFormat typeHintsFormat = new XmlJsonDataFormat();
                typeHintsFormat.setForceTopLevelObject(true);
                typeHintsFormat.setTypeHints("YES");
                // from XML to JSON
                from("direct:marshalTypeHints").marshal(typeHintsFormat).to("mock:jsonTypeHints");
                // from JSON to XML
                from("direct:unmarshalTypeHints").unmarshal(typeHintsFormat).to("mock:xmlTypeHints");
                
                XmlJsonDataFormat prefixedTypeHintsFormat = new XmlJsonDataFormat();
                prefixedTypeHintsFormat.setForceTopLevelObject(true);
                prefixedTypeHintsFormat.setTypeHints("WITH_PREFIX");
                // from XML to JSON
                from("direct:marshalPrefixedTypeHints").marshal(prefixedTypeHintsFormat).to("mock:jsonPrefixedTypeHints");

            }
        };
    }

}
