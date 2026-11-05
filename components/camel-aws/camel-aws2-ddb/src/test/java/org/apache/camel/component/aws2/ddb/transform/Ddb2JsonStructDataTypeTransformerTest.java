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
package org.apache.camel.component.aws2.ddb.transform;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.StreamRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddb2JsonStructDataTypeTransformerTest {

    private static final String JSON = "{\"name\": \"Rajesh\"}";

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final Ddb2JsonStructDataTypeTransformer transformer = new Ddb2JsonStructDataTypeTransformer();

    @TempDir
    Path dir;

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: the bodies transformerIn says, and a String after, as transformerOut says
        BodyTypes bodyTypes = Ddb2JsonStructDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(List.of(Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE, Carrier.MAP,
                Carrier.LIST, Carrier.POJO),
                List.of(bodyTypes.transformerIn().value()));
        assertEquals(Carrier.TEXT, bodyTypes.transformerOut().value()[0]);
        assertEquals(String.class.getCanonicalName(), bodyTypes.transformerOut().javaType());
        Record record = Record.builder().eventID("1").eventName(OperationType.INSERT)
                .dynamodb(StreamRecord.builder().approximateCreationDateTime(Instant.ofEpochSecond(1))
                        .newImage(Map.of("name", AttributeValue.builder().s("Rajesh").build())).build())
                .build();
        Path file = Files.writeString(dir.resolve("item.json"), JSON);
        for (Object body : List.of(record, "Test".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)), file.toFile(), 5,
                Map.of("key", "value"), List.of("a", "b"))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertInstanceOf(String.class, exchange.getMessage().getBody(), body.getClass().getName());
        }
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(record);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertTrue(exchange.getMessage().getBody(String.class).contains("Rajesh"), exchange.getMessage().getBody(String.class));

        // a String is left as it is
        exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(JSON);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertSame(JSON, exchange.getMessage().getBody());
    }

    @Test
    void aStreamIsReadAsText() throws Exception {
        // CAMEL-26300: Gson failed with a JsonIOException, as it cannot reflect into the stream classes of the JDK
        for (Object body : List.of(new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)), new StringReader(JSON),
                new InputStreamCache(JSON.getBytes(StandardCharsets.UTF_8)))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertEquals(JSON, exchange.getMessage().getBody(), body.getClass().getName());
        }
    }

    @Test
    void bytesAreReadAsText() throws Exception {
        // CAMEL-26300: Gson wrote bytes as a Json array of numbers, while the same Json in a String, a stream or a file
        // is taken as it is
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(JSON.getBytes(StandardCharsets.UTF_8));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals(JSON, exchange.getMessage().getBody());

        // the charset of the exchange applies, as for a stream
        String json = "{\"name\": \"Andr\u00e9s\"}";
        exchange = new DefaultExchange(camelContext);
        exchange.setProperty(Exchange.CHARSET_NAME, StandardCharsets.ISO_8859_1.name());
        exchange.getMessage().setBody(json.getBytes(StandardCharsets.ISO_8859_1));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals(json, exchange.getMessage().getBody());
    }

    @Test
    void aFileIsReadAsText() throws Exception {
        // CAMEL-26300: Gson failed with a JsonIOException, as it cannot reflect into java.io.File
        Path file = Files.writeString(dir.resolve("item.json"), JSON);
        for (Object body : List.of(file.toFile(), file,
                FileConsumer.asGenericFile(dir.toString(), file.toFile(), StandardCharsets.UTF_8.name(), false))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertEquals(JSON, exchange.getMessage().getBody(), body.getClass().getName());
        }
    }

    @Test
    void aFileThatCannotBeReadFails() {
        File missing = dir.resolve("missing.json").toFile();
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(missing);
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY));
        assertTrue(e.getMessage().startsWith("Failed to read the stream or file body as text"), e.getMessage());
        assertFalse(e.getMessage().contains("missing.json"), e.getMessage());
        assertSame(missing, exchange.getMessage().getBody());
    }
}
