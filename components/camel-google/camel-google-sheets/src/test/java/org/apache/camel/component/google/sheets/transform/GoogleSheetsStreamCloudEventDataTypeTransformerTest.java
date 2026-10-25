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
package org.apache.camel.component.google.sheets.transform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GoogleSheetsStreamCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final GoogleSheetsStreamCloudEventDataTypeTransformer transformer
            = new GoogleSheetsStreamCloudEventDataTypeTransformer();

    @Test
    void shouldMapToCloudEvent() {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, "sheet1");
        exchange.getMessage().setHeader(GoogleSheetsStreamConstants.SPREADSHEET_URL, "https://sheet1");
        exchange.getMessage().setBody("Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertEquals("org.apache.camel.event.google.sheets.stream.consume",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals("google.sheets.stream.sheet1", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertEquals("https://sheet1", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void shouldLookupDataTypeTransformer() {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("google-sheets-stream:application-cloudevents"));
        assertNotNull(transformer);
        assertEquals(GoogleSheetsStreamCloudEventDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    void theBodyTypesSayTheBody() {
        // CAMEL-25928: any body, as transformerIn says, which transformerOut says is left as it was
        BodyTypes bodyTypes = GoogleSheetsStreamCloudEventDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(Carrier.ANY, bodyTypes.transformerIn().value()[0]);
        assertEquals(Carrier.UNCHANGED, bodyTypes.transformerOut().value()[0]);
        for (Object body : List.of("Test", "Test".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)), 5)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertSame(body, exchange.getMessage().getBody(), body.getClass().getName());
            assertEquals("org.apache.camel.event.google.sheets.stream.consume",
                    exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        }
    }
}
