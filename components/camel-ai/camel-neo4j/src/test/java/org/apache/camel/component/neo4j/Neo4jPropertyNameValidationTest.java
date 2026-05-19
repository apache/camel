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
package org.apache.camel.component.neo4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jPropertyNameValidationTest {

    @Test
    void acceptsValidPropertyNames() {
        assertDoesNotThrow(() -> Neo4jProducer.validatePropertyName("name"));
        assertDoesNotThrow(() -> Neo4jProducer.validatePropertyName("_internal"));
        assertDoesNotThrow(() -> Neo4jProducer.validatePropertyName("firstName2"));
        assertDoesNotThrow(() -> Neo4jProducer.validatePropertyName("A_B_1"));
    }

    @Test
    void rejectsInvalidPropertyNames() {
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName(null));
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName(""));
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName("first name"));
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName("name-1"));
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName("name.sub"));
        assertThrows(IllegalArgumentException.class, () -> Neo4jProducer.validatePropertyName("1name"));
        // A property name that would otherwise change the structure of the generated query.
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jProducer.validatePropertyName("x) RETURN n UNION MATCH (m) RETURN m //"));
    }
}
