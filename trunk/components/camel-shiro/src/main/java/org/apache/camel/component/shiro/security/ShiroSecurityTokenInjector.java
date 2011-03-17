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
package org.apache.camel.component.shiro.security;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.util.ByteSource;

public class ShiroSecurityTokenInjector implements Processor {
    private final byte[] bits128 = {
        (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
        (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};
    private byte[] passPhrase;
    private ShiroSecurityToken securityToken;
    private CipherService cipherService;
    
    public ShiroSecurityTokenInjector() {
        this.passPhrase = bits128;

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
        ByteArrayOutputStream stream = new  ByteArrayOutputStream();
        ObjectOutput serialStream = new ObjectOutputStream(stream);
        serialStream.writeObject(securityToken);
        ByteSource byteSource = cipherService.encrypt(stream.toByteArray(), passPhrase);
        serialStream.close();
        stream.close();
        
        return byteSource;
    }

    public void process(Exchange exchange) throws Exception {
        exchange.getIn().setHeader("SHIRO_SECURITY_TOKEN", encrypt());
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
    
}
