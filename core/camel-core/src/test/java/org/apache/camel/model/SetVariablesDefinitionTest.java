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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Expression;
import org.apache.camel.TestSupport;
import org.junit.jupiter.api.Test;

class SetVariablesDefinitionTest extends TestSupport {

    @Test
    void testSetFromMap() {
        Map<String, Expression> map = new java.util.LinkedHashMap<>(3);
        map.put("fromBody", body());
        map.put("isCamel", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")));
        map.put("isHorse", ExpressionNodeHelper.toExpressionDefinition(body().contains("Horse")));
        SetVariablesDefinition def = new SetVariablesDefinition(map);
        assertNotNull(def.getVariables());
        assertEquals(3, def.getVariables().size());
        assertEquals("isCamel", def.getVariables().get(1).getName());
    }

    @Test
    void testSetFromMapOf() {
        SetVariablesDefinition def = new SetVariablesDefinition(
                Map.of("fromBody", body(), "isCamel", body().contains("Camel"), "isHorse", body().contains("Horse")));
        assertNotNull(def.getVariables());
        assertEquals(3, def.getVariables().size());
        Set<String> names = new java.util.HashSet<>();
        for (SetVariableDefinition varDef : def.getVariables()) {
            names.add(varDef.getName());
        }
        assertEquals(names, Set.of("fromBody", "isCamel", "isHorse"));
    }

    @Test
    void testSetFromVarargs() {
        SetVariablesDefinition def = new SetVariablesDefinition(
                "fromBody", body(),
                "isCamel", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")),
                "isHorse", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")));
        assertNotNull(def.getVariables());
        assertEquals(3, def.getVariables().size());
        assertEquals("isCamel", def.getVariables().get(1).getName());
    }

    @Test
    void testSetFromOnePair() {
        SetVariablesDefinition def = new SetVariablesDefinition("fromBody", body());
        assertNotNull(def.getVariables());
        assertEquals(1, def.getVariables().size());
        assertEquals("fromBody", def.getVariables().get(0).getName());
    }
}
