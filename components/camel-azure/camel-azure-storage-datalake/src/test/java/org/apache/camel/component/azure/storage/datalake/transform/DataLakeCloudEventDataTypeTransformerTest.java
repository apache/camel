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
package org.apache.camel.component.azure.storage.datalake.transform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.azure.storage.file.datalake.models.ArchiveStatus;
import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DataLakeCloudEventDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final DataLakeCloudEventDataTypeTransformer transformer = new DataLakeCloudEventDataTypeTransformer();

    @Test
    void theSourceIsTheFileSystem() throws Exception {
        // CAMEL-27101: the source was built from the archive status of the file
        for (String file : List.of("a.txt", "b.txt")) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setHeader(DataLakeConstants.CONSUMED_FILE_SYSTEM_NAME, "reports");
            exchange.getMessage().setHeader(DataLakeConstants.FILE_NAME, file);
            exchange.getMessage().setHeader(DataLakeConstants.E_TAG, "eTag-" + file);
            exchange.getMessage().setHeader(DataLakeConstants.ARCHIVE_STATUS, ArchiveStatus.REHYDRATE_PENDING_TO_HOT);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            assertEquals("azure.storage.datalake.reports",
                    exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
            assertEquals("eTag-" + file, exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
        }
    }

    @Test
    void theSourceIsNotTheArchiveStatus() throws Exception {
        // a message without the file system name of the consumer gets no source
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(DataLakeConstants.ARCHIVE_STATUS, ArchiveStatus.REHYDRATE_PENDING_TO_HOT);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, and the same body after, as transformerOut says
        BodyTypes bodyTypes = DataLakeCloudEventDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(Carrier.ANY, bodyTypes.transformerIn().value()[0]);
        assertEquals(Carrier.UNCHANGED, bodyTypes.transformerOut().value()[0]);
        for (Object body : Arrays.asList("Test", "Test".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)), Map.of("key", "value"), 5, null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertSame(body, exchange.getMessage().getBody(), String.valueOf(body));
            assertEquals("org.apache.camel.event.azure.storage.datalake.consume",
                    exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        }
    }
}
