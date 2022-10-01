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
package org.apache.camel.component.atlasmap;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atlasmap.java.test.SourceContact;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.xmlunit.builder.Input;
import org.xmlunit.xpath.JAXPXPathEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AtlasMapMultiDocsTest extends CamelSpringTestSupport {

    private static final String JSON_SOURCE = "{" +
                                              "\"firstName\": \"JsonFirstName\"," +
                                              "\"lastName\": \"JsonLastName\"," +
                                              "\"phoneNumber\": \"JsonPhoneNumber\"," +
                                              "\"zipCode\": \"JsonZipCode\"" +
                                              "}";

    private static final String XML_SOURCE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                             "<ns:Contact xmlns:ns=\"http://atlasmap.io/xml/test/v2\"\n" +
                                             "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                                             "    firstName=\"XmlFirstName\" lastName=\"XmlLastName\"\n" +
                                             "    phoneNumber=\"XmlPhoneNumber\" zipCode=\"XmlZipCode\" />\n";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapMultiDocsTest-context.xml");
    }

    @Test
    public void test() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        Map<String, Message> sourceMap = new HashMap<>();
        SourceContact javaSource = new SourceContact();
        javaSource.setFirstName("JavaFirstName");
        javaSource.setLastName("JavaLastName");
        javaSource.setPhoneNumber("JavaPhoneNumber");
        javaSource.setZipCode("JavaZipCode");
        Message msg = new DefaultMessage(context);
        msg.setBody(javaSource);
        msg.setHeader("testProp", "java-source-header");
        sourceMap.put("DOCID:JAVA:CONTACT:S", msg);
        msg = new DefaultMessage(context);
        msg.setBody(JSON_SOURCE);
        msg.setHeader("testProp", "json-source-header");
        sourceMap.put("DOCID:JSON:CONTACT:S", msg);
        msg = new DefaultMessage(context);
        msg.setBody(XML_SOURCE);
        msg.setHeader("testProp", "xml-source-header");
        sourceMap.put("DOCID:XML:CONTACT:S", msg);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBodyAndProperty("direct:start", null, "CAPTURED_OUT_MESSAGES_MAP", sourceMap);

        MockEndpoint.assertIsSatisfied(context);
        Exchange exchange = result.getExchanges().get(0);
        Map<?, ?> targetMap = exchange.getProperty("TARGET_MESSAGES_MAP", Map.class);

        verifyTargetDocs(targetMap);

        assertEquals("java-source-header", exchange.getProperty("target-exchange"));
        assertEquals("json-source-header", exchange.getProperty("testProp"));
        assertEquals("xml-source-header", exchange.getIn().getHeader("testProp"));
    }

    @Test
    public void testBody() throws Exception {
        MockEndpoint resultBody = getMockEndpoint("mock:result-body");
        resultBody.setExpectedCount(1);

        Map<String, Object> sourceMap = new HashMap<>();
        SourceContact javaSource = new SourceContact();
        javaSource.setFirstName("JavaFirstName");
        javaSource.setLastName("JavaLastName");
        javaSource.setPhoneNumber("JavaPhoneNumber");
        javaSource.setZipCode("JavaZipCode");
        sourceMap.put("DOCID:JAVA:CONTACT:S", javaSource);
        sourceMap.put("DOCID:JSON:CONTACT:S", JSON_SOURCE);
        sourceMap.put("DOCID:XML:CONTACT:S", XML_SOURCE);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody("direct:start-body", sourceMap);

        MockEndpoint.assertIsSatisfied(context);
        Exchange exchange = resultBody.getExchanges().get(0);
        Map<?, ?> targetMap = exchange.getIn().getBody(Map.class);

        verifyTargetDocs(targetMap);
    }

    @Test
    public void testHeaderDocs() throws Exception {
        MockEndpoint resultBody = getMockEndpoint("mock:result-body");
        resultBody.setExpectedCount(1);

        Map<String, Object> sourceMap = new HashMap<>();
        SourceContact javaSource = new SourceContact();
        javaSource.setFirstName("JavaFirstName");
        javaSource.setLastName("JavaLastName");
        javaSource.setPhoneNumber("JavaPhoneNumber");
        javaSource.setZipCode("JavaZipCode");
        sourceMap.put("DOCID:JAVA:CONTACT:S", javaSource);
        sourceMap.put("DOCID:JSON:CONTACT:S", JSON_SOURCE);
        sourceMap.put("DOCID:XML:CONTACT:S", XML_SOURCE);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBodyAndHeaders("direct:start-header", null, sourceMap);

        MockEndpoint.assertIsSatisfied(context);
        Exchange exchange = resultBody.getExchanges().get(0);
        verifyTargetDocs(exchange.getIn().getHeaders());
    }

    @Test
    public void testExchangePropertyDocs() throws Exception {
        MockEndpoint resultBody = getMockEndpoint("mock:result-body");
        resultBody.setExpectedCount(1);

        Map<String, Object> sourceMap = new HashMap<>();
        SourceContact javaSource = new SourceContact();
        javaSource.setFirstName("JavaFirstName");
        javaSource.setLastName("JavaLastName");
        javaSource.setPhoneNumber("JavaPhoneNumber");
        javaSource.setZipCode("JavaZipCode");
        sourceMap.put("DOCID:JAVA:CONTACT:S", javaSource);
        sourceMap.put("DOCID:JSON:CONTACT:S", JSON_SOURCE);
        sourceMap.put("DOCID:XML:CONTACT:S", XML_SOURCE);

        Endpoint ep = context.getEndpoint("direct:start-exchange-property");
        Exchange ex = ep.createExchange();
        ex.getProperties().putAll(sourceMap);
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.send(ep, ex);

        MockEndpoint.assertIsSatisfied(context);
        Exchange exchange = resultBody.getExchanges().get(0);
        verifyTargetDocs(exchange.getProperties());
    }

    private void verifyTargetDocs(Map<?, ?> targetMap) throws Exception {
        String jsonTarget = (String) targetMap.get("DOCID:JSON:CONTACT:T");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonTargetNode = mapper.readTree(jsonTarget);
        assertEquals("JsonFirstName", jsonTargetNode.get("firstName").asText());
        assertEquals("JavaLastName", jsonTargetNode.get("lastName").asText());
        assertEquals("XmlPhoneNumber", jsonTargetNode.get("phoneNumber").asText());

        String xmlTarget = (String) targetMap.get("DOCID:XML:CONTACT:T");
        HashMap<String, String> ns = new HashMap<>();
        ns.put("ns", "http://atlasmap.io/xml/test/v2");
        JAXPXPathEngine xpath = new JAXPXPathEngine();
        xpath.setNamespaceContext(ns);
        assertEquals("XmlFirstName", xpath.evaluate("/Contact/@firstName", Input.fromString(xmlTarget).build()));
        assertEquals("JsonLastName", xpath.evaluate("/Contact/@lastName", Input.fromString(xmlTarget).build()));
        assertEquals("JavaPhoneNumber", xpath.evaluate("/Contact/@phoneNumber", Input.fromString(xmlTarget).build()));
    }

}
