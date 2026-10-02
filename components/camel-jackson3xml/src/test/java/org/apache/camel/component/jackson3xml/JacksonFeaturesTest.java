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

import java.util.Arrays;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.dataformat.xml.XmlWriteFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonFeaturesTest extends CamelTestSupport {

    @Test
    public void testEnableWrongSyntaxOfFeature() throws Exception {
        JacksonXMLDataFormat format = new JacksonXMLDataFormat();
        format.setEnableFeatures("Package.Enum.FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals("Enable feature: Package.Enum.FEATURE cannot contain more than one '.'", ex.getMessage());
    }

    @Test
    public void testEnableWrongTypedFeatureWithClassName() throws Exception {
        JacksonXMLDataFormat format = new JacksonXMLDataFormat();
        format.setEnableFeatures("Enum.FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals(
                "Enable feature: Enum.FEATURE cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]",
                ex.getMessage());
    }

    @Test
    public void testEnableWrongTypedFeatureWithoutClassName() throws Exception {
        JacksonXMLDataFormat format = new JacksonXMLDataFormat();
        format.setEnableFeatures("FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals(
                "Enable feature: FEATURE cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature,XmlReadFeature,XmlWriteFeature]",
                ex.getMessage());
    }

    @Test
    public void testEnableStrictDuplicateDetectionFromDifferentFeatures() throws Exception {
        // Initialization by implementation order (StreamReadFeature before StreamWriteFeature)
        JacksonXMLDataFormat format1 = new JacksonXMLDataFormat();
        format1.setEnableFeatures("STRICT_DUPLICATE_DETECTION");
        format1.setCamelContext(context());
        format1.doStart();
        assertTrue(format1.getXmlMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertFalse(format1.getXmlMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));

        // Explicit declaration of implementing feature set
        JacksonXMLDataFormat format2 = new JacksonXMLDataFormat();
        format2.setEnableFeatures("StreamReadFeature.STRICT_DUPLICATE_DETECTION");
        format2.setCamelContext(context());
        format2.doStart();
        assertTrue(format2.getXmlMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertFalse(format2.getXmlMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));

        JacksonXMLDataFormat format3 = new JacksonXMLDataFormat();
        format3.setEnableFeatures("StreamWriteFeature.STRICT_DUPLICATE_DETECTION");
        format3.setCamelContext(context());
        format3.doStart();
        assertFalse(format3.getXmlMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertTrue(format3.getXmlMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));
    }

    @Test
    public void testEnableMapperFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("<ArrayList><item>test</item><item/></ArrayList>");

        template.send("direct:format", exchange -> exchange.getIn().setBody(Arrays.asList("test", null)));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                JacksonXMLDataFormat formatModel = new JacksonXMLDataFormat();
                formatModel.disableFeature(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL);

                from("direct:format").marshal(formatModel).to("mock:result");
            }
        };
    }
}
