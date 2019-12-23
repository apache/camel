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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.util.ByteSource;

public class ShiroSecurityTokenInjector implements Processor {
    private byte[] passPhrase;
    private ShiroSecurityToken securityToken;
    private CipherService cipherService;
    private boolean base64;

    public ShiroSecurityTokenInjector() {
        // Set up AES encryption based cipher service, by default
        cipherService = new AesCipherService();
    }

    public ShiroSecurityTokenInjector(ShiroSecurityToken securityToken, byte[] passPhrase) {
        this();
        this.setSecurityToken(securityToken);
        this.setPassPhrase(passPhrase);
    }

    public ShiroSecurityTokenInjector(ShiroSecurityToken securityToken, byte[] passPhrase, CipherService cipherService) {
        this(securityToken, passPhrase);
        this.cipherService = cipherService;
    }

    public ByteSource encrypt() throws Exception {
        return ShiroSecurityHelper.encrypt(securityToken, passPhrase, cipherService);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ByteSource bytes = encrypt();

        Object token;
        if (isBase64()) {
            token = bytes.toBase64();
        } else {
            token = bytes;
        }

        exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, token);
    }

    public byte[] getPassPhrase() {
        return passPhrase;
    }

    public void setPassPhrase(byte[] passPhrase) {
        this.passPhrase = passPhrase;
    }

    public void setSecurityToken(ShiroSecurityToken securityToken) {
        this.securityToken = securityToken;
    }

    public ShiroSecurityToken getSecurityToken() {
        return securityToken;
    }

    public CipherService getCipherService() {
        return cipherService;
    }

    public void setCipherService(CipherService cipherService) {
        this.cipherService = cipherService;
    }

    public boolean isBase64() {
        return base64;
    }

    public void setBase64(boolean base64) {
        this.base64 = base64;
    }

}
