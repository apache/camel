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
package org.apache.camel.component.aws2.lambda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaOperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(18, Lambda2Operations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(Lambda2Operations.createFunction, Lambda2Operations.valueOf("createFunction"));
        assertEquals(Lambda2Operations.getFunction, Lambda2Operations.valueOf("getFunction"));
        assertEquals(Lambda2Operations.listFunctions, Lambda2Operations.valueOf("listFunctions"));
        assertEquals(Lambda2Operations.invokeFunction, Lambda2Operations.valueOf("invokeFunction"));
        assertEquals(Lambda2Operations.deleteFunction, Lambda2Operations.valueOf("deleteFunction"));
        assertEquals(Lambda2Operations.updateFunction, Lambda2Operations.valueOf("updateFunction"));
        assertEquals(Lambda2Operations.createEventSourceMapping, Lambda2Operations.valueOf("createEventSourceMapping"));
        assertEquals(Lambda2Operations.deleteEventSourceMapping, Lambda2Operations.valueOf("deleteEventSourceMapping"));
        assertEquals(Lambda2Operations.listEventSourceMapping, Lambda2Operations.valueOf("listEventSourceMapping"));
        assertEquals(Lambda2Operations.listTags, Lambda2Operations.valueOf("listTags"));
        assertEquals(Lambda2Operations.tagResource, Lambda2Operations.valueOf("tagResource"));
        assertEquals(Lambda2Operations.untagResource, Lambda2Operations.valueOf("untagResource"));
        assertEquals(Lambda2Operations.publishVersion, Lambda2Operations.valueOf("publishVersion"));
        assertEquals(Lambda2Operations.listVersions, Lambda2Operations.valueOf("listVersions"));
        assertEquals(Lambda2Operations.createAlias, Lambda2Operations.valueOf("createAlias"));
        assertEquals(Lambda2Operations.deleteAlias, Lambda2Operations.valueOf("deleteAlias"));
        assertEquals(Lambda2Operations.getAlias, Lambda2Operations.valueOf("getAlias"));
        assertEquals(Lambda2Operations.listAliases, Lambda2Operations.valueOf("listAliases"));
    }

    @Test
    public void testToString() {
        assertEquals("createFunction", Lambda2Operations.createFunction.toString());
        assertEquals("getFunction", Lambda2Operations.getFunction.toString());
        assertEquals("listFunctions", Lambda2Operations.listFunctions.toString());
        assertEquals("invokeFunction", Lambda2Operations.invokeFunction.toString());
        assertEquals("deleteFunction", Lambda2Operations.deleteFunction.toString());
        assertEquals("updateFunction", Lambda2Operations.updateFunction.toString());
        assertEquals("createEventSourceMapping", Lambda2Operations.createEventSourceMapping.toString());
        assertEquals("deleteEventSourceMapping", Lambda2Operations.deleteEventSourceMapping.toString());
        assertEquals("listEventSourceMapping", Lambda2Operations.listEventSourceMapping.toString());
        assertEquals("listTags", Lambda2Operations.listTags.toString());
        assertEquals("tagResource", Lambda2Operations.tagResource.toString());
        assertEquals("untagResource", Lambda2Operations.untagResource.toString());
        assertEquals("publishVersion", Lambda2Operations.publishVersion.toString());
        assertEquals("listVersions", Lambda2Operations.listVersions.toString());
        assertEquals("createAlias", Lambda2Operations.createAlias.toString());
        assertEquals("deleteAlias", Lambda2Operations.deleteAlias.toString());
        assertEquals("getAlias", Lambda2Operations.getAlias.toString());
        assertEquals("listAliases", Lambda2Operations.listAliases.toString());
    }
}
