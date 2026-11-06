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
package org.apache.camel.component.file.azure;

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CatalogCamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The azure-files consumer implements the read locks none, rename and changed, and false and off mean none. Any other
 * readLock fails when the consumer is created, instead of consuming the files without a read lock.
 */
class FilesEndpointReadLockTest extends CamelTestSupport {

    @BindToRegistry("repo")
    private final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository();

    @ParameterizedTest
    @ValueSource(strings = { "markerFile", "fileLock", "idempotent", "idempotent-changed", "idempotent-rename" })
    void unsupportedReadLockFailsToCreateConsumer(String readLock) {
        Endpoint endpoint
                = context.getEndpoint("azure-files://account/share/inbox?readLock=" + readLock + "&idempotentRepository=#repo");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> endpoint.createConsumer(exchange -> {
        }));
        assertEquals("ReadLock invalid: " + readLock + ", must be one of: none,rename,changed", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "false", "off", "rename", "changed" })
    void supportedReadLockCreatesConsumer(String readLock) throws Exception {
        Endpoint endpoint = context.getEndpoint("azure-files://account/share/inbox?readLock=" + readLock);

        assertNotNull(endpoint.createConsumer(exchange -> {
        }));
    }

    @Test
    void readLockListsOnlyTheSupportedReadLocks() throws Exception {
        // the catalog, the Endpoint DSL and the tooling offer only the read locks that the consumer accepts
        String json = ((CatalogCamelContext) context).getComponentParameterJsonSchema("azure-files");
        JsonObject properties = (JsonObject) ((JsonObject) Jsoner.deserialize(json)).get("properties");
        JsonObject readLock = (JsonObject) properties.get("readLock");

        assertEquals(new JsonArray(List.of("none", "rename", "changed")), readLock.get("enum"));
        assertEquals("none", readLock.get("defaultValue"));
    }
}
