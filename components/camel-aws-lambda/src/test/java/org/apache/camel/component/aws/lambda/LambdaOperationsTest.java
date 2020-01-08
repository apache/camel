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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
        assertEquals(LambdaOperations.createFunction.toString(), "createFunction");
        assertEquals(LambdaOperations.getFunction.toString(), "getFunction");
        assertEquals(LambdaOperations.listFunctions.toString(), "listFunctions");
        assertEquals(LambdaOperations.invokeFunction.toString(), "invokeFunction");
        assertEquals(LambdaOperations.deleteFunction.toString(), "deleteFunction");
        assertEquals(LambdaOperations.updateFunction.toString(), "updateFunction");
        assertEquals(LambdaOperations.createEventSourceMapping.toString(), "createEventSourceMapping");
        assertEquals(LambdaOperations.deleteEventSourceMapping.toString(), "deleteEventSourceMapping");
        assertEquals(LambdaOperations.listEventSourceMapping.toString(), "listEventSourceMapping");
        assertEquals(LambdaOperations.listTags.toString(), "listTags");
        assertEquals(LambdaOperations.tagResource.toString(), "tagResource");
        assertEquals(LambdaOperations.untagResource.toString(), "untagResource");
        assertEquals(LambdaOperations.publishVersion.toString(), "publishVersion");
        assertEquals(LambdaOperations.listVersions.toString(), "listVersions");
        assertEquals(LambdaOperations.createAlias.toString(), "createAlias");
        assertEquals(LambdaOperations.deleteAlias.toString(), "deleteAlias");
        assertEquals(LambdaOperations.getAlias.toString(), "getAlias");
        assertEquals(LambdaOperations.listAliases.toString(), "listAliases");
    }
}
