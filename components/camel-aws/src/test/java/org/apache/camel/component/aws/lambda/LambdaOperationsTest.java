/**
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
        assertEquals(6, LambdaOperations.values().length);
    }

    @Test
    public void valueOf() {
        assertEquals(LambdaOperations.createFunction, LambdaOperations.valueOf("createFunction"));
        assertEquals(LambdaOperations.getFunction, LambdaOperations.valueOf("getFunction"));
        assertEquals(LambdaOperations.listFunctions, LambdaOperations.valueOf("listFunctions"));
        assertEquals(LambdaOperations.invokeFunction, LambdaOperations.valueOf("invokeFunction"));
        assertEquals(LambdaOperations.deleteFunction, LambdaOperations.valueOf("deleteFunction"));
        assertEquals(LambdaOperations.updateFunction, LambdaOperations.valueOf("updateFunction"));
    }

    @Test
    public void testToString() {
        assertEquals(LambdaOperations.createFunction.toString(), "createFunction");
        assertEquals(LambdaOperations.getFunction.toString(), "getFunction");
        assertEquals(LambdaOperations.listFunctions.toString(), "listFunctions");
        assertEquals(LambdaOperations.invokeFunction.toString(), "invokeFunction");
        assertEquals(LambdaOperations.deleteFunction.toString(), "deleteFunction");
        assertEquals(LambdaOperations.updateFunction.toString(), "updateFunction");
    }
}
