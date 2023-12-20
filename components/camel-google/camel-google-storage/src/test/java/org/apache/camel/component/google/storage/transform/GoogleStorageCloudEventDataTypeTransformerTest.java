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

package org.apache.camel.component.google.storage.transform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleStorageCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final GoogleStorageCloudEventDataTypeTransformer transformer = new GoogleStorageCloudEventDataTypeTransformer();

    @Test
    void shouldMapToCloudEvent() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, "test1");
        exchange.getMessage().setHeader(GoogleCloudStorageConstants.BUCKET_NAME, "myBucket");
        exchange.getMessage().setBody(new ByteArrayInputStream("Test1".getBytes(StandardCharsets.UTF_8)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        Assertions.assertTrue(exchange.getMessage().getHeaders().containsKey(GoogleCloudStorageConstants.OBJECT_NAME));
        assertEquals("org.apache.camel.event.google.storage.downloadTo",
                exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals("test1", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        assertEquals("google.storage.bucket.myBucket", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("google-storage:application-cloudevents"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(GoogleStorageCloudEventDataTypeTransformer.class, transformer.getClass());
    }

}
