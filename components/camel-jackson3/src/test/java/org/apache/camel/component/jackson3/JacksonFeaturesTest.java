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
package org.apache.camel.component.jackson3;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonFeaturesTest extends CamelTestSupport {

    @Test
    public void testEnableWrongSyntaxOfFeature() throws Exception {
        JacksonDataFormat format = new JacksonDataFormat();
        format.setEnableFeatures("Package.Enum.FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals("Enable feature: Package.Enum.FEATURE cannot contain more than one '.'", ex.getMessage());
    }

    @Test
    public void testEnableWrongTypedFeatureWithClassName() throws Exception {
        JacksonDataFormat format = new JacksonDataFormat();
        format.setEnableFeatures("Enum.FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals(
                "Enable feature: Enum.FEATURE cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature]",
                ex.getMessage());
    }

    @Test
    public void testEnableWrongTypedFeatureWithoutClassName() throws Exception {
        JacksonDataFormat format = new JacksonDataFormat();
        format.setEnableFeatures("FEATURE");
        format.setCamelContext(context());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> format.doStart());
        assertEquals(
                "Enable feature: FEATURE cannot be converted to an accepted enum of types [SerializationFeature,DeserializationFeature,MapperFeature,DateTimeFeature,EnumFeature,JsonNodeFeature,StreamReadFeature,StreamWriteFeature]",
                ex.getMessage());
    }

    @Test
    public void testEnableStrictDuplicateDetectionFromDifferentFeatures() throws Exception {
        // Initialization by implementation order (StreamReadFeature before StreamWriteFeature)
        JacksonDataFormat format1 = new JacksonDataFormat();
        format1.setEnableFeatures("STRICT_DUPLICATE_DETECTION");
        format1.setCamelContext(context());
        format1.doStart();
        assertTrue(format1.getObjectMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertFalse(format1.getObjectMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));

        // Explicit declaration of implementing feature set
        JacksonDataFormat format2 = new JacksonDataFormat();
        format2.setEnableFeatures("StreamReadFeature.STRICT_DUPLICATE_DETECTION");
        format2.setCamelContext(context());
        format2.doStart();
        assertTrue(format2.getObjectMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertFalse(format2.getObjectMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));

        JacksonDataFormat format3 = new JacksonDataFormat();
        format3.setEnableFeatures("StreamWriteFeature.STRICT_DUPLICATE_DETECTION");
        format3.setCamelContext(context());
        format3.doStart();
        assertFalse(format3.getObjectMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        assertTrue(format3.getObjectMapper().isEnabled(StreamWriteFeature.STRICT_DUPLICATE_DETECTION));
    }

    @Test
    public void testEnableDeserializationFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();

        template.send("direct:format", exchange -> exchange.getIn().setBody("[]"));

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableMapperFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TestPojo.class);

        template.send("direct:format", exchange -> exchange.getIn().setBody("{\"nAmE\": \"test\"}"));

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableDatatypeFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("123");

        template.send("direct:unformat", exchange -> exchange.getIn().setBody(new Date(123)));

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableDatatypeFeatureViaModelString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("123");

        template.send("direct:unformat-model", exchange -> exchange.getIn().setBody(new Date(123)));

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableStreamWriteFeatureFeature() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("123000");

        template.send("direct:unformat", exchange -> exchange.getIn().setBody(new BigDecimal("123e+3")));

        mock.assertIsSatisfied();
    }

    @Test
    public void testEnableStreamWriteFeatureViaModelString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo("123000");

        template.send("direct:unformat-model", exchange -> exchange.getIn().setBody(new BigDecimal("123e+3")));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                JacksonDataFormat format = new JacksonDataFormat(TestPojo.class);
                format.enableFeature(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);
                format.enableFeature(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
                format.disableFeature(SerializationFeature.INDENT_OUTPUT);
                format.disableFeature(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
                format.disableFeature(MapperFeature.APPLY_DEFAULT_VALUES);
                format.enableFeature(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
                format.enableFeature(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);

                from("direct:format").unmarshal(format).to("mock:result");
                from("direct:unformat").marshal(format).to("mock:result");

                JacksonDataFormat formatModel = new JacksonDataFormat();
                formatModel.setEnableFeatures("WRITE_DATES_AS_TIMESTAMPS,WRITE_BIGDECIMAL_AS_PLAIN");

                from("direct:unformat-model").marshal(formatModel).to("mock:result");
            }
        };
    }
}
