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
package org.apache.camel.component.shiro.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShiroSecurityHelperTest {

    @Test
    public void testSerializeAndDeserialize() {
        test("user", "password");
    }

    @Test
    public void testSerializeAndDeserializeEmptyPassword() {
        test("user", "");
        test("user", null);
    }

    private void test(String username, String password) {
        ShiroSecurityToken token = new ShiroSecurityToken(username, password);

        byte[] data = ShiroSecurityHelper.serialize(token);
        ShiroSecurityToken deserializedToken = ShiroSecurityHelper.deserialize(data);

        assertEquals(token.getUsername(), deserializedToken.getUsername());
        assertEquals(token.getPassword() != null ? token.getPassword() : "", deserializedToken.getPassword());
    }
}
