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
package org.apache.camel.component.sjms.jms;

import java.util.HashMap;

import com.example.external.NotAllowedPayload;
import org.apache.camel.support.DefaultExchangeHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsBindingDeserializationFilterTest {

    private JmsBinding newBinding(String filter) {
        return new JmsBinding(true, true, null, null, null, null, filter);
    }

    @Test
    public void testDefaultFilterAllowsStandardJavaType() {
        JmsBinding binding = newBinding(null);
        assertDoesNotThrow(() -> binding.checkDeserializedClass(new HashMap<>()));
    }

    @Test
    public void testDefaultFilterAllowsCamelExchangeHolder() {
        JmsBinding binding = newBinding(null);
        assertDoesNotThrow(() -> binding.checkDeserializedClass(new DefaultExchangeHolder()));
    }

    @Test
    public void testDefaultFilterAllowsNullPayload() {
        JmsBinding binding = newBinding(null);
        assertDoesNotThrow(() -> binding.checkDeserializedClass(null));
    }

    @Test
    public void testDefaultFilterRejectsClassOutsideAllowList() {
        JmsBinding binding = newBinding(null);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> binding.checkDeserializedClass(new NotAllowedPayload()));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains(NotAllowedPayload.class.getName()));
    }

    @Test
    public void testConfiguredFilterAllowsCustomClass() {
        JmsBinding binding = newBinding("com.example.external.*;java.**;javax.**;org.apache.camel.**;!*");
        assertDoesNotThrow(() -> binding.checkDeserializedClass(new NotAllowedPayload()));
    }
}
