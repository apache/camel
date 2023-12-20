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
package org.apache.camel.component.platform.http.vertx;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class VertxPlatformHttpEngineWithTypeConverterTest {

    @Test
    public void testByteBufferConversion() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        TypeConverter tc = mockByteBufferTypeConverter();
        context.getTypeConverterRegistry().addTypeConverter(ByteBuffer.class, Map.class, tc);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/bytebuffer")
                            .routeId("bytebuffer")
                            .setBody().constant(Collections.singletonMap("bb", "my-test"));
                }
            });

            context.start();

            given()
                    .when()
                    .get("/bytebuffer")
                    .then()
                    .statusCode(200)
                    .body(equalTo("ByteBuffer:my-test"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testInputStreamConversion() throws Exception {
        final CamelContext context = VertxPlatformHttpEngineTest.createCamelContext();

        TypeConverter tc = mockInputStreamTypeConverter();
        context.getTypeConverterRegistry().addTypeConverter(InputStream.class, Map.class, tc);

        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/inputstream")
                            .routeId("inputstream")
                            .setBody().constant(Collections.singletonMap("is", "my-test"));
                }
            });

            context.start();

            given()
                    .when()
                    .get("/inputstream")
                    .then()
                    .statusCode(200)
                    .body(equalTo("InputStream:my-test"));
        } finally {
            context.stop();
        }
    }

    private TypeConverter mockByteBufferTypeConverter() {
        return new MockTypeConverter() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                byte[] out = ("ByteBuffer:" + ((Map) value).get("bb")).getBytes(StandardCharsets.UTF_8);
                return type.cast(ByteBuffer.wrap(out));
            }
        };
    }

    private TypeConverter mockInputStreamTypeConverter() {
        return new MockTypeConverter() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                byte[] out = ("InputStream:" + ((Map) value).get("is")).getBytes(StandardCharsets.UTF_8);
                return type.cast(new ByteArrayInputStream(out));
            }

        };
    }

    abstract static class MockTypeConverter implements TypeConverter {
        @Override
        public boolean allowNull() {
            return false;
        }

        @Override
        public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
            return null;
        }

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return null;
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Object value)
                throws TypeConversionException {
            return null;
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value)
                throws TypeConversionException {
            return null;
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Object value) {
            return null;
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
            return null;
        }
    }
}
