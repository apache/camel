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
package org.apache.camel.component.jackson3xml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.dataformat.xml.XmlMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * REST binding rejects unknown properties with Jackson 3 too, whose default is to accept them.
 */
public class JacksonXMLRestBindingFailOnUnknownPropertiesTest {

    private static final String UNKNOWN = "<TestPojo><name>Camel</name><donotexists>foo</donotexists></TestPojo>";

    private CamelContext context;

    @BeforeEach
    public void createContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void stopContext() {
        context.stop();
    }

    @Test
    public void testUnknownPropertyIsRejected() throws Exception {
        JacksonXMLDataFormat xml = setup(Map.of());

        assertEquals("FAIL_ON_UNKNOWN_PROPERTIES", xml.getEnableFeatures());
        assertThrows(UnrecognizedPropertyException.class, () -> unmarshal(xml, UNKNOWN));
    }

    @Test
    public void testOtherFeaturesKeepTheRejection() throws Exception {
        JacksonXMLDataFormat xml = setup(Map.of("xml.in.enableFeatures", "ACCEPT_SINGLE_VALUE_AS_ARRAY"));

        assertEquals("ACCEPT_SINGLE_VALUE_AS_ARRAY,FAIL_ON_UNKNOWN_PROPERTIES", xml.getEnableFeatures());
        assertThrows(UnrecognizedPropertyException.class, () -> unmarshal(xml, UNKNOWN));
    }

    @Test
    public void testDisablingTheFeatureAcceptsUnknownProperties() throws Exception {
        JacksonXMLDataFormat xml = setup(Map.of("xml.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES"));

        TestPojo pojo = (TestPojo) unmarshal(xml, UNKNOWN);
        assertEquals("Camel", pojo.getName());
    }

    @Test
    public void testOwnMapperKeepsItsSettings() throws Exception {
        JacksonXMLDataFormat xml = setup(Map.of("xml.in.xmlMapper", new XmlMapper()));

        assertNull(xml.getEnableFeatures());
        TestPojo pojo = (TestPojo) unmarshal(xml, UNKNOWN);
        assertEquals("Camel", pojo.getName());
    }

    private JacksonXMLDataFormat setup(Map<String, Object> properties) throws Exception {
        RestConfiguration config = new RestConfiguration();
        config.setDataFormatProperties(properties);

        JacksonXMLDataFormat xml = new JacksonXMLDataFormat();
        JacksonXMLDataFormat outXml = new JacksonXMLDataFormat();
        new JacksonXMLRestBindingJacksonXmlDataFormatFactory()
                .setupJacksonXml(context, config, null, TestPojo.class, null, null, xml, outXml);
        xml.setCamelContext(context);
        xml.start();
        return xml;
    }

    private static Object unmarshal(JacksonXMLDataFormat xml, String body) throws Exception {
        Exchange exchange = new DefaultExchange(xml.getCamelContext());
        return xml.unmarshal(exchange, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
}
