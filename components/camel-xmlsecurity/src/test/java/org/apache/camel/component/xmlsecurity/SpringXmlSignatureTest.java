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
package org.apache.camel.component.xmlsecurity;

import java.security.KeyPair;

import javax.xml.crypto.KeySelector;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spring.SpringCamelContext;

public class SpringXmlSignatureTest extends XmlSignatureTest {

    private static KeyPair rsaPair;

    protected CamelContext createCamelContext() throws Exception {
        rsaPair = getKeyPair("RSA", 1024);
        return SpringCamelContext.springCamelContext("/org/apache/camel/component/xmlsecurity/SpringXmlSignatureTests.xml");
    }

    public static KeyAccessor getDsaKeyAccessor() {
        return getKeyAccessor(getKeyPair("DSA", 1024).getPrivate());
    }

    public static KeyAccessor getRsaKeyAccessor() {
        return getKeyAccessor(rsaPair.getPrivate());
    }

    public static KeySelector getDsaKeySelector() {
        return KeySelector.singletonKeySelector(getKeyPair("DSA", 1024).getPublic());
    }

    public static KeySelector getRsaKeySelector() {
        return KeySelector.singletonKeySelector(rsaPair.getPublic());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        return super.createRegistry();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {};
    }

}