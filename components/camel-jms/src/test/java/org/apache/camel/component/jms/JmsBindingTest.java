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
package org.apache.camel.component.jms;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.apache.activemq.command.ActiveMQBlobMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JmsBindingTest {

    @Test
    public void testJmsBindingNoArgs() throws Exception {
        JmsBinding underTest = new JmsBinding();
        assertNull(underTest.extractBodyFromJms(null, new ActiveMQBlobMessage()));
    }

    @Test
    public void testGetValidJmsHeaderValueWithBigInteger() {
        JmsBinding binding = new JmsBinding();
        Object value = binding.getValidJMSHeaderValue("foo", new BigInteger("12345"));
        assertEquals("12345", value);
    }

    @Test
    public void testGetValidJmsHeaderValueWithBigDecimal() {
        JmsBinding binding = new JmsBinding();
        Object value = binding.getValidJMSHeaderValue("foo", new BigDecimal("123.45"));
        assertEquals("123.45", value);
    }
}
