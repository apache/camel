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
package org.apache.camel.jsonpath;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonPathCustomMapperTest extends CamelTestSupport {

    static class CustomDoubleSerializer extends JsonSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeRawValue(String.format(Locale.US, "%.6f", value));
        }
    }

    static class CustomModule extends SimpleModule {
        CustomModule() {
            addSerializer(Double.class, new CustomDoubleSerializer());
        }

        @Override
        public String getModuleName() {
            return getClass().getSimpleName();
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        ObjectMapper customMapper = new ObjectMapper();
        Module doubleModule = new CustomModule();
        customMapper.registerModule(doubleModule);
        registry.bind("customMapper", customMapper);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:jsonAsString")
                        .split().jsonpathWriteAsString("$")
                        .log("${body}")
                        .to("mock:resultString");
            }
        };
    }

    @Test
    public void testJsonPathWriteCustomDoubles() throws Exception {
        // /CAMEL-17956
        MockEndpoint m = getMockEndpoint("mock:resultString");
        m.expectedMessageCount(1);
        template.sendBody("direct:jsonAsString", new File("src/test/resources/bignumbers.json"));
        Object resultFromMock = m.getReceivedExchanges().get(0).getMessage().getBody();
        assertTrue(resultFromMock instanceof String);
        assertTrue(resultFromMock.toString().contains("121002700.0"));
        assertTrue(resultFromMock.toString().contains("-91000000.0"));

    }

}
