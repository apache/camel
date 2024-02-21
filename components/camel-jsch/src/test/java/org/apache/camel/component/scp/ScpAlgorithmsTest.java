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
package org.apache.camel.component.scp;

import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.server.ServerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ScpAlgorithmsTest extends ScpServerTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + getScpPath() + "?recursive=true&delete=true")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Override
    public void setUp() {
    }

    @Override
    public void tearDown() {
    }

    @Test
    public void testScpSimpleProduceWithDifferentAlgorithms() throws Exception {
        Config[] configs = new Config[] {
                //                new Config("hostkey-dsa.pem", BuiltinSignatures.dsa, BuiltinDHFactories.dhg14_256, BuiltinCiphers.aes128ctr),
                new Config(
                        "hostkey-rsa.pem", BuiltinSignatures.rsaSHA256, BuiltinDHFactories.dhg16_512,
                        BuiltinCiphers.aes192ctr),
                new Config(
                        "hostkey-rsa.pem", BuiltinSignatures.rsaSHA512, BuiltinDHFactories.dhg16_512,
                        BuiltinCiphers.aes192ctr),
                new Config(
                        "hostkey-ecdsa256.pem", BuiltinSignatures.nistp256, BuiltinDHFactories.ecdhp256,
                        BuiltinCiphers.aes128ctr),
                new Config(
                        "hostkey-ecdsa384.pem", BuiltinSignatures.nistp384, BuiltinDHFactories.ecdhp384,
                        BuiltinCiphers.aes192ctr),
                new Config(
                        "hostkey-ecdsa521.pem", BuiltinSignatures.nistp521, BuiltinDHFactories.ecdhp521,
                        BuiltinCiphers.aes256ctr),
                new Config(
                        "hostkey-ed25519.pem", BuiltinSignatures.ed25519, BuiltinDHFactories.curve25519,
                        BuiltinCiphers.aes256gcm)
        };

        for (final Config config : configs) {
            LOG.info("Starting SSH server with {} config", config);
            this.serverConfigurer = sshd -> {
                sshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get(config.privateKeyLocation)));
                List<NamedFactory<Signature>> signatureFactories = sshd.getSignatureFactories();
                signatureFactories.clear();
                signatureFactories.add(config.signatureAlgorithm);
                sshd.setSignatureFactories(signatureFactories);
                List<KeyExchangeFactory> keyExchangeFactories = sshd.getKeyExchangeFactories();
                keyExchangeFactories.clear();
                keyExchangeFactories
                        .add(ServerBuilder.DH2KEX.apply(BuiltinDHFactories.resolveFactory(config.kexAlgorithm.getName())));
                sshd.setKeyExchangeFactories(keyExchangeFactories);
                List<NamedFactory<Cipher>> cipherFactories = sshd.getCipherFactories();
                cipherFactories.clear();
                cipherFactories.add(config.cipher);
                sshd.setCipherFactories(cipherFactories);
            };
            super.setUp();

            assumeTrue(this.isSetupComplete());

            getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

            String uri = getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
            template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

            MockEndpoint.assertIsSatisfied(context);
            super.tearDown();
        }
    }

    private static final class Config {
        final String privateKeyLocation;
        final BuiltinSignatures signatureAlgorithm;
        final BuiltinDHFactories kexAlgorithm;
        final BuiltinCiphers cipher;

        private Config(String privateKeyLocation, BuiltinSignatures signatureAlgorithm, BuiltinDHFactories kexAlgorithm,
                       BuiltinCiphers cipher) {
            this.privateKeyLocation = "src/test/resources/keys/" + privateKeyLocation;
            this.signatureAlgorithm = signatureAlgorithm;
            this.kexAlgorithm = kexAlgorithm;
            this.cipher = cipher;
        }

        @Override
        public String toString() {
            return String.format("%s: %s/%s/%s", privateKeyLocation, signatureAlgorithm, kexAlgorithm, cipher);
        }
    }

}
