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
package org.apache.camel.component.gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GsonDataFormatTest {

    @Mock
    private Exchange exchange;
    @Mock
    private Message message;

    @BeforeEach
    private void setup() {
        when(message.getHeader(Exchange.CHARSET_NAME, String.class)).thenReturn(StandardCharsets.UTF_8.name());
        when(exchange.getIn()).thenReturn(message);
    }

    @Test
    public void testString() throws Exception {
        testJson("\"A string\"", "A string");
    }

    @Test
    public void testMap() throws Exception {
        testJson("{value=123}", Collections.singletonMap("value", 123.0));
    }

    @Test
    public void testList() throws Exception {
        testJson("[{value=123}]", Collections.singletonList(Collections.singletonMap("value", 123.0)));
    }

    private void testJson(String json, Object expected) throws Exception {
        Object unmarshalled;
        try (GsonDataFormat gsonDataFormat = new GsonDataFormat()) {
            gsonDataFormat.doStart();
            try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                unmarshalled = gsonDataFormat.unmarshal(exchange, in);
            }
            assertEquals(expected, unmarshalled);
        }
    }
}
