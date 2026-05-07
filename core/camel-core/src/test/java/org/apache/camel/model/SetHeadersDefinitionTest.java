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

import java.util.Map;
import java.util.Set;

import org.apache.camel.Expression;
import org.apache.camel.TestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SetHeadersDefinitionTest extends TestSupport {

    @Test
    void testSetFromMap() {
        Map<String, Expression> headerMap = new java.util.LinkedHashMap<>(3);
        headerMap.put("fromBody", body());
        headerMap.put("isCamel", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")));
        headerMap.put("isHorse", ExpressionNodeHelper.toExpressionDefinition(body().contains("Horse")));
        SetHeadersDefinition setHeadersDef = new SetHeadersDefinition(headerMap);
        assertNotNull(setHeadersDef.getHeaders());
        assertEquals(3, setHeadersDef.getHeaders().size());
        assertEquals("isCamel", setHeadersDef.getHeaders().get(1).getName());
    }

    @Test
    void testSetFromMapOf() {
        SetHeadersDefinition setHeadersDef = new SetHeadersDefinition(
                Map.of("fromBody", body(),
                        "isCamel", body().contains("Camel"), "isHorse", body().contains("Horse")));
        assertNotNull(setHeadersDef.getHeaders());
        assertEquals(3, setHeadersDef.getHeaders().size());
        Set<String> names = new java.util.HashSet<>();
        for (SetHeaderDefinition setHdrDef : setHeadersDef.getHeaders()) {
            names.add(setHdrDef.getName());
        }
        assertEquals(names, Set.of("fromBody", "isCamel", "isHorse"));
    }

    @Test
    void testSetFromVarargs() {
        SetHeadersDefinition setHeadersDef = new SetHeadersDefinition(
                "fromBody", body(),
                "isCamel", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")),
                "isHorse", ExpressionNodeHelper.toExpressionDefinition(body().contains("Camel")));
        assertNotNull(setHeadersDef.getHeaders());
        assertEquals(3, setHeadersDef.getHeaders().size());
        assertEquals("isCamel", setHeadersDef.getHeaders().get(1).getName());
    }

    @Test
    void testSetFromOnePair() {
        SetHeadersDefinition setHeadersDef = new SetHeadersDefinition("fromBody", body());
        assertNotNull(setHeadersDef.getHeaders());
        assertEquals(1, setHeadersDef.getHeaders().size());
        assertEquals("fromBody", setHeadersDef.getHeaders().get(0).getName());
    }

}
