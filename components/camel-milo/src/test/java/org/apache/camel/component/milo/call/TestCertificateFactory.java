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

package org.apache.camel.component.milo.call;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.security.RsaSha256CertificateFactory;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

/**
 * copied from milo::TestCertificateFactory
 */
public class TestCertificateFactory extends RsaSha256CertificateFactory {

    @Override
    public KeyPair createKeyPair(NodeId nodeId) {
        assert nodeId.equals(NodeIds.RsaSha256ApplicationCertificateType);

        return createRsaSha256KeyPair();
    }

    @Override
    public X509Certificate[] createCertificateChain(NodeId nodeId, KeyPair keyPair) {
        assert nodeId.equals(NodeIds.RsaSha256ApplicationCertificateType);

        return createRsaSha256CertificateChain(keyPair);
    }

    @Override
    public KeyPair createRsaSha256KeyPair() {
        try {
            return SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public X509Certificate[] createRsaSha256CertificateChain(KeyPair keyPair) {
        String applicationUri = "urn:eclipse:milo:test";

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("Eclipse Milo OPC UA Test Server")
                .setOrganization("digitalpetri")
                .setOrganizationalUnit("dev")
                .setLocalityName("Folsom")
                .setStateName("CA")
                .setCountryCode("US")
                .setApplicationUri(applicationUri)
                .addIpAddress("127.0.0.1")
                .addDnsName("localhost");

        try {
            return new X509Certificate[] {builder.build()};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
