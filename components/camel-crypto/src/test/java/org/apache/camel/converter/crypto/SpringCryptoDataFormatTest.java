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
package org.apache.camel.converter.crypto;

import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringCryptoDataFormatTest extends CryptoDataFormatTest {

    private static Key deskey;
    private static Key desEdekey;
    private static Key aeskey;

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {};
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("DES");
        deskey = generator.generateKey();
        generator = KeyGenerator.getInstance("DESede");
        desEdekey = generator.generateKey();
        generator = KeyGenerator.getInstance("AES");
        aeskey = generator.generateKey();
        return SpringCamelContext.springCamelContext(
                new ClassPathXmlApplicationContext("/org/apache/camel/component/crypto/SpringCryptoDataFormatTest.xml"), true);
    }

    public static Key getDesKey() {
        return deskey;
    }

    public static Key getDesEdeKey() {
        return desEdekey;
    }

    public static Key getAESKey() {
        return aeskey;
    }

    public static byte[] getIV() {
        return new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    }

    public static GCMParameterSpec getGCMParameterSpec() {
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        return new GCMParameterSpec(128, iv);
    }
}
