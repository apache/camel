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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonIOException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.dynamodb.model.Record;
import software.amazon.awssdk.services.dynamodb.model.StreamRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ddb2JsonStructDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final Ddb2JsonStructDataTypeTransformer transformer = new Ddb2JsonStructDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: the bodies transformerIn says, and a String after, as transformerOut says
        BodyTypes bodyTypes = Ddb2JsonStructDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(List.of(Carrier.TEXT, Carrier.BYTES, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO),
                List.of(bodyTypes.transformerIn().value()));
        assertEquals(Carrier.TEXT, bodyTypes.transformerOut().value()[0]);
        assertEquals(String.class.getCanonicalName(), bodyTypes.transformerOut().javaType());
        Record record = Record.builder().eventID("1").eventName(OperationType.INSERT)
                .dynamodb(StreamRecord.builder().approximateCreationDateTime(Instant.ofEpochSecond(1))
                        .newImage(Map.of("name", AttributeValue.builder().s("Rajesh").build())).build())
                .build();
        for (Object body : List.of(record, "Test".getBytes(StandardCharsets.UTF_8), 5, Map.of("key", "value"),
                List.of("a", "b"))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertInstanceOf(String.class, exchange.getMessage().getBody(), body.getClass().getName());
        }
        // Gson cannot write the fields of a stream or a file of the JDK
        for (Object body : List.of(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)), new File("item.json"))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            assertThrows(JsonIOException.class, () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY),
                    body.getClass().getName());
        }
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(record);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertTrue(exchange.getMessage().getBody(String.class).contains("Rajesh"), exchange.getMessage().getBody(String.class));

        // a String is left as it is
        String text = "{\"name\": \"Rajesh\"}";
        exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(text);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertSame(text, exchange.getMessage().getBody());
    }
}
