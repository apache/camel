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

package org.apache.camel.model;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TryDefinitionTest {

    @Test
    public void doFinallyTest() {
        TryDefinition tryDefinition = new TryDefinition();
        CatchDefinition catchDefinition = new CatchDefinition();
        FinallyDefinition finallyDefinition = new FinallyDefinition();
        tryDefinition.addOutput(new ToDefinition("mock:1"));
        catchDefinition.setExceptions(List.of("java.lang.Exception"));
        catchDefinition.addOutput(new ToDefinition("mock:2"));
        finallyDefinition.addOutput(new ToDefinition("mock:3"));
        tryDefinition.addOutput(catchDefinition);
        tryDefinition.addOutput(finallyDefinition);
        Assertions.assertDoesNotThrow(tryDefinition::preCreateProcessor);
        TryDefinition tryDefinition1 = tryDefinition.copyDefinition();
        Assertions.assertDoesNotThrow(tryDefinition1::preCreateProcessor);

        FinallyDefinition finallyDefinition1 = new FinallyDefinition();
        finallyDefinition.addOutput(new ToDefinition("mock:4"));
        tryDefinition.addOutput(finallyDefinition1);
        Assertions.assertThrows(IllegalArgumentException.class, tryDefinition::preCreateProcessor);
    }
}
