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
package org.apache.camel.dataformat.ocsf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OcsfDataFormatMapperTest {

    public static class Finding {
        public String zone = "a";
        public Instant time = Instant.ofEpochSecond(1706000000L);
        public String activity = "b";
    }

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    private OcsfDataFormat started(OcsfDataFormat dataFormat) {
        dataFormat.setCamelContext(context);
        dataFormat.start();
        return dataFormat;
    }

    private String marshal(OcsfDataFormat dataFormat, Object body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dataFormat.marshal(new DefaultExchange(context), body, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void defaultMapperKeepsItsOutput() throws Exception {
        OcsfDataFormat dataFormat = started(new OcsfDataFormat(Finding.class));

        assertThat(marshal(dataFormat, new Finding()))
                .isEqualTo("{\"zone\":\"a\",\"time\":1706000000.000000000,\"activity\":\"b\"}");
    }

    @Test
    void defaultMapperIgnoresUnknownProperties() throws Exception {
        OcsfDataFormat dataFormat = started(new OcsfDataFormat(Finding.class));
        Exchange exchange = new DefaultExchange(context);

        Finding finding = (Finding) dataFormat.unmarshal(exchange, (Object) "{\"zone\":\"z\",\"unknown\":1}");

        assertThat(finding.zone).isEqualTo("z");
    }

    @Test
    void jsonNodeBodyIsRead() throws Exception {
        OcsfDataFormat dataFormat = started(new OcsfDataFormat(Finding.class));
        Exchange exchange = new DefaultExchange(context);
        Object body = JsonMapper.builder().build().readTree("{\"zone\":\"z\"}");

        Finding finding = (Finding) dataFormat.unmarshal(exchange, body);

        assertThat(finding.zone).isEqualTo("z");
    }

    @Test
    void prettyPrintAppliesWithoutChangingACustomMapper() throws Exception {
        ObjectMapper custom = JsonMapper.builder().build();
        OcsfDataFormat dataFormat = new OcsfDataFormat(custom, Finding.class);
        dataFormat.setPrettyPrint(true);
        started(dataFormat);

        assertThat(marshal(dataFormat, new Finding())).contains(System.lineSeparator());
        assertThat(custom.isEnabled(SerializationFeature.INDENT_OUTPUT)).isFalse();
    }

    @Test
    void singleMapperInTheRegistryIsUsed() throws Exception {
        ObjectMapper registered = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT).build();
        context.getRegistry().bind("mapper", ObjectMapper.class, registered);
        OcsfDataFormat dataFormat = started(new OcsfDataFormat(Finding.class));

        assertThat(dataFormat.getObjectMapper()).isSameAs(registered);
    }
}
