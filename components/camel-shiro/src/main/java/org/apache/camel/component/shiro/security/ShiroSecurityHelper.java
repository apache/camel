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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.util.ByteSource;

public final class ShiroSecurityHelper {

    private static Pattern pattern = Pattern.compile("(\\d+):(.+)");

    private ShiroSecurityHelper() {
    }

    public static ByteSource encrypt(
            ShiroSecurityToken securityToken, byte[] passPhrase, CipherService cipherService) {
        byte[] data = serialize(securityToken);
        return cipherService.encrypt(data, passPhrase);
    }

    static byte[] serialize(ShiroSecurityToken token) {
        StringBuilder sb = new StringBuilder().append(token.getUsername().length())
                .append(":")
                .append(token.getUsername())
                .append(token.getPassword() != null ? token.getPassword() : "");
        return sb.toString().getBytes();
    }

    public static ShiroSecurityToken deserialize(byte[] data) {
        String text = new String(data);

        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalStateException("Can not deserialize security token - token is probably corrupted.");
        }
        int length = Integer.parseInt(matcher.group(1));

        String username = matcher.group(2).substring(0, length);
        String password = matcher.group(2).substring(length);

        return new ShiroSecurityToken(username, password);
    }
}
