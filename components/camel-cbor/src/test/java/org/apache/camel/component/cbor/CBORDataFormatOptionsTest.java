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
package org.apache.camel.component.cbor;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CBORDataFormatOptionsTest {

    private static final BigInteger NEGATIVE_BIG = new BigInteger("-18446744073709551617");
    private static final String NEGATIVE_BIG_JACKSON2 = "c349010000000000000001";

    private CamelContext context;

    public static class Bean {
        public String b = "b";
        public String a = "a";
    }

    public static class DateBean {
        public Date date = new Date(0);
    }

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private CBORDataFormat init(CBORDataFormat format) throws Exception {
        format.setCamelContext(context);
        format.init();
        return format;
    }

    private byte[] marshal(CBORDataFormat format, Object body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        format.marshal(new DefaultExchange(context), body, out);
        return out.toByteArray();
    }

    private Object unmarshal(CBORDataFormat format, byte[] body) throws Exception {
        return format.unmarshal(new DefaultExchange(context), body);
    }

    @Test
    void defaultsAreThoseOfJackson3() throws Exception {
        CBORDataFormat format = init(new CBORDataFormat());
        CBORMapper plain = new CBORMapper();

        assertEquals(HexFormat.of().formatHex(plain.writeValueAsBytes(new Bean())),
                HexFormat.of().formatHex(marshal(format, new Bean())));
        Map<?, ?> date = plain.readValue(marshal(format, new DateBean()), Map.class);
        assertEquals("1970-01-01T00:00:00.000Z", date.get("date"));
    }

    @Test
    void enableFeaturesTakesEffect() throws Exception {
        CBORDataFormat format = new CBORDataFormat();
        format.setUnmarshalType(Bean.class);
        format.setEnableFeatures("FAIL_ON_UNKNOWN_PROPERTIES,WRITE_DATES_AS_TIMESTAMPS");
        init(format);

        byte[] unknown = new CBORMapper().writeValueAsBytes(Map.of("unknown", 1));
        Exception e = assertThrows(Exception.class, () -> unmarshal(format, unknown));
        assertInstanceOf(UnrecognizedPropertyException.class, e);

        Map<?, ?> date = new CBORMapper().readValue(marshal(format, new DateBean()), Map.class);
        assertEquals(0, date.get("date"));
    }

    @Test
    void disableFeaturesTakesEffect() throws Exception {
        CBORDataFormat format = new CBORDataFormat();
        format.setDisableFeatures("SORT_PROPERTIES_ALPHABETICALLY");
        init(format);

        Map<?, ?> bean = new CBORMapper().readValue(marshal(format, new Bean()), Map.class);
        assertEquals("[b, a]", bean.keySet().toString());
    }

    @Test
    void prettyPrintIsSetOnTheMapper() throws Exception {
        CBORDataFormat format = new CBORDataFormat();
        format.setPrettyPrint(true);
        init(format);

        assertTrue(format.getObjectMapper().isEnabled(SerializationFeature.INDENT_OUTPUT));
        assertInstanceOf(CBORFactory.class, format.getObjectMapper().tokenStreamFactory());
    }

    @Test
    void unknownFeatureFails() {
        CBORDataFormat format = new CBORDataFormat();
        format.setEnableFeatures("NOT_A_FEATURE");

        assertThrows(Exception.class, () -> init(format));
    }

    @Test
    void negativeBigIntegerKeepsItsEncoding() throws Exception {
        CBORDataFormat format = init(new CBORDataFormat());

        assertEquals(NEGATIVE_BIG_JACKSON2, HexFormat.of().formatHex(marshal(format, NEGATIVE_BIG)));
        assertEquals(NEGATIVE_BIG, unmarshal(format, HexFormat.of().parseHex(NEGATIVE_BIG_JACKSON2)));
    }

    @Test
    void negativeBigIntegerKeepsItsEncodingWithOptions() throws Exception {
        CBORDataFormat format = new CBORDataFormat();
        format.setEnableFeatures("WRITE_DATES_AS_TIMESTAMPS");
        init(format);

        assertEquals(NEGATIVE_BIG_JACKSON2, HexFormat.of().formatHex(marshal(format, NEGATIVE_BIG)));
    }

    @Test
    void simpleValueIsReadAsANumber() throws Exception {
        CBORDataFormat format = init(new CBORDataFormat());

        assertEquals(5, unmarshal(format, new byte[] { (byte) 0xe5 }));
    }

    @Test
    void cborMapperInRegistryIsPromoted() throws Exception {
        ObjectMapper mapper = new CBORMapper();
        context.getRegistry().bind("cborMapper", ObjectMapper.class, mapper);

        CBORDataFormat format = init(new CBORDataFormat());

        assertSame(mapper, format.getObjectMapper());
    }

    @Test
    void optionsApplyToACopyOfTheMapperInRegistry() throws Exception {
        ObjectMapper mapper = new CBORMapper();
        context.getRegistry().bind("cborMapper", ObjectMapper.class, mapper);

        CBORDataFormat format = new CBORDataFormat();
        format.setPrettyPrint(true);
        init(format);

        assertNotSame(mapper, format.getObjectMapper());
        assertTrue(format.getObjectMapper().isEnabled(SerializationFeature.INDENT_OUTPUT));
        assertFalse(mapper.isEnabled(SerializationFeature.INDENT_OUTPUT));
    }

    @Test
    void jsonMapperInRegistryIsIgnored() throws Exception {
        context.getRegistry().bind("jsonMapper", ObjectMapper.class, new JsonMapper());

        CBORDataFormat format = init(new CBORDataFormat());

        assertInstanceOf(CBORFactory.class, format.getObjectMapper().tokenStreamFactory());
    }
}
