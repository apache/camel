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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.apache.camel.util.IOHelper;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.util.ByteSource;

public final class ShiroSecurityHelper {

    private ShiroSecurityHelper() {
    }

    public static ByteSource encrypt(ShiroSecurityToken securityToken, byte[] passPhrase, CipherService cipherService) throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ObjectOutput serialStream = new ObjectOutputStream(stream);
        try {
            serialStream.writeObject(securityToken);
            return cipherService.encrypt(stream.toByteArray(), passPhrase);
        } finally {
            close(serialStream);
            IOHelper.close(stream);
        }
    }

    private static void close(ObjectOutput output) {
        try {
            output.close();
        } catch (IOException e) {
            // ignore
        }
    }

}
