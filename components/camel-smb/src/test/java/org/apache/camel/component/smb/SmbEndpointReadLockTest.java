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
package org.apache.camel.component.smb;

import java.util.List;

import org.apache.camel.CatalogCamelContext;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The smb consumer implements the read locks none, rename and changed, and the readLock option lists only them.
 */
class SmbEndpointReadLockTest extends CamelTestSupport {

    @Test
    void readLockListsOnlyTheSupportedReadLocks() throws Exception {
        // the catalog, the Endpoint DSL and the tooling offer only the read locks that the consumer accepts
        String json = ((CatalogCamelContext) context).getComponentParameterJsonSchema("smb");
        JsonObject properties = (JsonObject) ((JsonObject) Jsoner.deserialize(json)).get("properties");
        JsonObject readLock = (JsonObject) properties.get("readLock");

        assertEquals(new JsonArray(List.of("none", "rename", "changed")), readLock.get("enum"));
        assertEquals("none", readLock.get("defaultValue"));
    }
}
