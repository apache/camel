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
package org.apache.camel.component.aws.lambda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaOperationsTest {

    @Test
    public void supportedOperationCount() {
        assertEquals(18, LambdaOperations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(LambdaOperations.createFunction, LambdaOperations.valueOf("createFunction"));
        assertEquals(LambdaOperations.getFunction, LambdaOperations.valueOf("getFunction"));
        assertEquals(LambdaOperations.listFunctions, LambdaOperations.valueOf("listFunctions"));
        assertEquals(LambdaOperations.invokeFunction, LambdaOperations.valueOf("invokeFunction"));
        assertEquals(LambdaOperations.deleteFunction, LambdaOperations.valueOf("deleteFunction"));
        assertEquals(LambdaOperations.updateFunction, LambdaOperations.valueOf("updateFunction"));
        assertEquals(LambdaOperations.createEventSourceMapping, LambdaOperations.valueOf("createEventSourceMapping"));
        assertEquals(LambdaOperations.deleteEventSourceMapping, LambdaOperations.valueOf("deleteEventSourceMapping"));
        assertEquals(LambdaOperations.listEventSourceMapping, LambdaOperations.valueOf("listEventSourceMapping"));
        assertEquals(LambdaOperations.listTags, LambdaOperations.valueOf("listTags"));
        assertEquals(LambdaOperations.tagResource, LambdaOperations.valueOf("tagResource"));
        assertEquals(LambdaOperations.untagResource, LambdaOperations.valueOf("untagResource"));
        assertEquals(LambdaOperations.publishVersion, LambdaOperations.valueOf("publishVersion"));
        assertEquals(LambdaOperations.listVersions, LambdaOperations.valueOf("listVersions"));
        assertEquals(LambdaOperations.createAlias, LambdaOperations.valueOf("createAlias"));
        assertEquals(LambdaOperations.deleteAlias, LambdaOperations.valueOf("deleteAlias"));
        assertEquals(LambdaOperations.getAlias, LambdaOperations.valueOf("getAlias"));
        assertEquals(LambdaOperations.listAliases, LambdaOperations.valueOf("listAliases"));
    }

    @Test
    public void testToString() {
        assertEquals("createFunction", LambdaOperations.createFunction.toString());
        assertEquals("getFunction", LambdaOperations.getFunction.toString());
        assertEquals("listFunctions", LambdaOperations.listFunctions.toString());
        assertEquals("invokeFunction", LambdaOperations.invokeFunction.toString());
        assertEquals("deleteFunction", LambdaOperations.deleteFunction.toString());
        assertEquals("updateFunction", LambdaOperations.updateFunction.toString());
        assertEquals("createEventSourceMapping", LambdaOperations.createEventSourceMapping.toString());
        assertEquals("deleteEventSourceMapping", LambdaOperations.deleteEventSourceMapping.toString());
        assertEquals("listEventSourceMapping", LambdaOperations.listEventSourceMapping.toString());
        assertEquals("listTags", LambdaOperations.listTags.toString());
        assertEquals("tagResource", LambdaOperations.tagResource.toString());
        assertEquals("untagResource", LambdaOperations.untagResource.toString());
        assertEquals("publishVersion", LambdaOperations.publishVersion.toString());
        assertEquals("listVersions", LambdaOperations.listVersions.toString());
        assertEquals("createAlias", LambdaOperations.createAlias.toString());
        assertEquals("deleteAlias", LambdaOperations.deleteAlias.toString());
        assertEquals("getAlias", LambdaOperations.getAlias.toString());
        assertEquals("listAliases", LambdaOperations.listAliases.toString());
    }
}
