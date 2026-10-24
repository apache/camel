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
package org.apache.camel.component.solr.converter;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SolrResponseConverterTest {

    private CamelContext context;
    private TypeConverter converter;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();
        converter = context.getTypeConverter();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void textIsNotConvertedToMap() {
        assertNull(converter.tryConvertTo(Map.class, "hello"));
        assertThrows(NoTypeConversionAvailableException.class, () -> converter.mandatoryConvertTo(Map.class, "hello"));
    }

    @Test
    public void pojoIsNotConvertedToMap() {
        assertNull(converter.tryConvertTo(Map.class, new Pojo()));
        assertThrows(NoTypeConversionAvailableException.class,
                () -> converter.mandatoryConvertTo(Map.class, new Pojo()));
    }

    @Test
    public void textIsNotConvertedToSolrPingResponse() {
        assertNull(converter.tryConvertTo(SolrPingResponse.class, "hello"));
        assertThrows(NoTypeConversionAvailableException.class,
                () -> converter.mandatoryConvertTo(SolrPingResponse.class, "hello"));
    }

    @Test
    public void namedListIsConvertedToMap() throws Exception {
        NamedList<Object> header = new NamedList<>();
        header.add("status", 0);
        NamedList<Object> response = new NamedList<>();
        response.add("responseHeader", header);
        response.add("status", "OK");

        Map<?, ?> map = converter.mandatoryConvertTo(Map.class, response);
        assertEquals("OK", map.get("status"));
        assertEquals(0, map.get("responseHeader.status"));

        SolrPingResponse ping = converter.mandatoryConvertTo(SolrPingResponse.class, response);
        assertNotNull(ping);
        assertEquals(0, ping.getStatus());
    }

    public static class Pojo {
        private String name = "camel";

        public String getName() {
            return name;
        }
    }
}
