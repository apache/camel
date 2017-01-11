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
package org.apache.camel.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MemoryStateRepositoryTest {
    @Test
    public void shouldSaveState() throws Exception {
        // Given an empty FileStateRepository
        MemoryStateRepository repository = new MemoryStateRepository();

        // When saving a state
        repository.setState("key", "value");

        // Then it should be retrieved afterwards
        assertEquals("value", repository.getState("key"));
    }

    @Test
    public void shouldUpdateState() throws Exception {
        // Given a FileStateRepository with a state in it
        MemoryStateRepository repository = new MemoryStateRepository();
        repository.setState("key", "value");

        // When updating the state
        repository.setState("key", "value2");

        // Then the new value should be retrieved afterwards
        assertEquals("value2", repository.getState("key"));
    }
}
