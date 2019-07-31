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
package org.apache.camel.component.xmlsecurity.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.xml.crypto.KeySelector;

import org.apache.camel.component.xmlsecurity.api.DefaultKeyAccessor;
import org.apache.camel.component.xmlsecurity.api.DefaultKeySelector;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;

public final class TestKeystore {

    private TestKeystore() {
        // helper class
    }

 
    public static KeyAccessor getKeyAccessor(String alias) throws Exception {

        DefaultKeyAccessor accessor = new DefaultKeyAccessor();
        accessor.setKeyStore(getKeyStore());
        accessor.setPassword(getPassword());
        accessor.setAlias(alias);
        return accessor;
    }

    public static KeySelector getKeySelector(String alias) throws Exception {
        DefaultKeySelector selector = new DefaultKeySelector();
        selector.setKeyStore(getKeyStore());
        selector.setAlias(alias);
        return selector;
    }

    private static char[] getPassword() {
        return "abcd1234".toCharArray();
    }

    public static KeyStore getKeyStore() throws GeneralSecurityException, IOException {

        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream is = TestKeystore.class.getClassLoader().getResourceAsStream("org/apache/camel/component/xmlsecurity/keystore.jks");
        ks.load(is, null);

        return ks;
    }
}