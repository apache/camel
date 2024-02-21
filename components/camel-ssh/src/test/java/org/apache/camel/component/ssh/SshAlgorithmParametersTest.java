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
package org.apache.camel.component.ssh;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SshAlgorithmParametersTest extends CamelTestSupport {

    private int port = AvailablePortFinder.getNextAvailable();

    private String sshEndpointURI = "ssh://smx:smx@localhost:" + port + "?timeout=3000" +
                                    "&ciphers=aes192-ctr" +
                                    "&macs=hmac-sha1-etm@openssh.com,hmac-sha2-256,hmac-sha1" +
                                    "&kex=ecdh-sha2-nistp521" +
                                    "&signatures=rsa-sha2-512,ssh-rsa-cert-v01@openssh.com" +
                                    "&compressions=zlib,none";

    private String customClientSshEndpointURI = "ssh://smx:smx@localhost:" + port + "?timeout=3000&clientBuilder=#myClient";

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        ClientBuilder clientBuilder = new ClientBuilder();

        List<NamedFactory<Cipher>> cipherFactories = Arrays.asList(BuiltinCiphers.aes192ctr);

        clientBuilder.cipherFactories(cipherFactories);
        List<NamedFactory<Signature>> signatureFactories
                = Arrays.asList(BuiltinSignatures.rsaSHA512, BuiltinSignatures.rsa_cert);

        clientBuilder.signatureFactories(signatureFactories);
        List<NamedFactory<Mac>> macFactories
                = Arrays.asList(BuiltinMacs.hmacsha1etm, BuiltinMacs.hmacsha256, BuiltinMacs.hmacsha1);

        clientBuilder.macFactories(macFactories);
        List<NamedFactory<Compression>> compressionFactories
                = Arrays.asList(BuiltinCompressions.zlib, BuiltinCompressions.none);

        clientBuilder.compressionFactories(compressionFactories);
        List<KeyExchangeFactory> kexFactories = NamedFactory.setUpTransformedFactories(false,
                Arrays.asList(BuiltinDHFactories.ecdhp521), ClientBuilder.DH2KEX);

        clientBuilder.keyExchangeFactories(kexFactories);

        registry.bind("myClient", clientBuilder);
    }

    /**
     * Test if all algorithm query parameters are set to SshClient object.
     */
    @Test
    public void producerCiphersParameterTest() throws Exception {
        context.getComponent("ssh", SshComponent.class);
        SshEndpoint endpoint = context.getEndpoint(sshEndpointURI, SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);
        checkParameters(client);
    }

    @Test
    public void consumerCiphersParameterTest() throws Exception {
        context.getComponent("ssh", SshComponent.class);
        SshEndpoint endpoint = context.getEndpoint(sshEndpointURI, SshEndpoint.class);
        SshConsumer consumer = (SshConsumer) endpoint.createConsumer(x -> {
        });
        consumer.start();
        SshClient client = (SshClient) FieldUtils.readField(consumer, "client", true);
        checkParameters(client);
    }

    @Test
    public void consumerCustomClientParameterTest() throws Exception {
        context.getComponent("ssh", SshComponent.class);
        SshEndpoint endpoint = context.getEndpoint(customClientSshEndpointURI, SshEndpoint.class);
        SshConsumer consumer = (SshConsumer) endpoint.createConsumer(x -> {
        });
        consumer.start();
        SshClient client = (SshClient) FieldUtils.readField(consumer, "client", true);
        checkParameters(client);
    }

    @Test
    public void producerCustomClientParameterTest() throws Exception {
        context.getComponent("ssh", SshComponent.class);
        SshEndpoint endpoint = context.getEndpoint(customClientSshEndpointURI, SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);
        checkParameters(client);
    }

    private void checkParameters(SshClient client) {

        //Ciphers
        Assertions.assertEquals(1, client.getCipherFactoriesNames().size());
        Assertions.assertTrue(client.getCipherFactoriesNames().contains("aes192-ctr"));

        //Macs
        Assertions.assertEquals(3, client.getMacFactoriesNames().size());
        Assertions.assertTrue(client.getMacFactoriesNames().contains("hmac-sha1-etm@openssh.com"),
                "Contains hmac-sha1-etm@openssh.com check");
        Assertions.assertTrue(client.getMacFactoriesNames().contains("hmac-sha2-256"), "Contains hmac-sha2-256 check");
        Assertions.assertTrue(client.getMacFactoriesNames().contains("hmac-sha1"), "Contains hmac-sha1 check");

        //Kex
        List<String> keyExchangeFactoriesNames
                = client.getKeyExchangeFactories().stream().map(x -> x.getName()).collect(Collectors.toList());
        Assertions.assertEquals(1, keyExchangeFactoriesNames.size());
        Assertions.assertTrue(keyExchangeFactoriesNames.contains("ecdh-sha2-nistp521"), "Contains ecdh-sha2-nistp521 check");

        //Compressions
        Assertions.assertEquals(2, client.getCompressionFactoriesNames().size());
        Assertions.assertTrue(client.getCompressionFactoriesNames().contains("zlib"), "Contains zlib check");
        Assertions.assertTrue(client.getCompressionFactoriesNames().contains("none"), "Contains none check");
    }
}
