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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25751: a Map or a List written to a file - a body unmarshalled from JSON - says to marshal it first.
 */
public class FileProducerMapBodyTest {

    @TempDir
    Path dir;

    @Test
    public void testMapBody() throws Exception {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", "1001");
        String message = failure(order, null);
        assertTrue(message.startsWith("Cannot store file: "), message);
        assertTrue(message.contains("but has type: java.util.LinkedHashMap"), message);
        assertTrue(message.contains(
                "a Map is not file content: marshal it first (marshal: json) or set the body to the text"), message);
        assertFalse(Files.exists(dir.resolve("order.json")));
    }

    @Test
    public void testListBodyWithCharset() throws Exception {
        // a charset writes through a reader, which is made from the same stream
        String message = failure(List.of(Map.of("sku", "A1")), "UTF-8");
        assertTrue(message.contains("a List is not file content: marshal it first (marshal: json)"), message);
    }

    @Test
    public void testOtherBodyKeepsTheMessage() throws Exception {
        String message = failure(new Object(), null);
        assertTrue(message.contains("No body available of type: java.io.InputStream"), message);
        assertFalse(message.contains("is not file content"), message);
    }

    private String failure(Object body, String charset) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            FileEndpoint endpoint = new FileEndpoint("file:" + dir, new FileComponent(context));
            endpoint.setFile(dir.toFile());
            if (charset != null) {
                endpoint.setCharset(charset);
            }
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(body);
            FileOperations operations = new FileOperations(endpoint);
            GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.storeFile(dir.resolve("order.json").toString(), exchange, -1));
            assertInstanceOf(InvalidPayloadException.class, e.getCause());
            return e.getMessage();
        }
    }
}
