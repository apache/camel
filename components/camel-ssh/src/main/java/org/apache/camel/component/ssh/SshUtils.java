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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;

import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.cipher.CipherFactory;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.compression.CompressionFactory;
import org.apache.sshd.common.helpers.AbstractFactoryManager;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.DHFactory;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.mac.MacFactory;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.signature.SignatureFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.joining;
import static org.apache.sshd.common.util.GenericUtils.isBlank;

public class SshUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SshUtils.class);

    public static <S> List<NamedFactory<S>> filter(
            Class<S> type,
            Collection<NamedFactory<S>> factories, String[] names) {
        List<NamedFactory<S>> list = new ArrayList<>();
        LOGGER.trace("List of available {} algorithms : {}", type.getSimpleName().toLowerCase(),
                factories.stream().map(NamedResource::getName).collect(joining(",")));
        for (String name : names) {
            name = name.trim();
            boolean found = false;
            for (NamedFactory<S> factory : factories) {
                if (factory.getName().equals(name)) {
                    list.add(factory);
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOGGER.warn("Configured {} '{}' not available", type.getSimpleName().toLowerCase(), name);
            }
        }
        return list;
    }

    public static List<KeyExchangeFactory> filter(List<KeyExchangeFactory> factories, String[] names) {
        List<KeyExchangeFactory> list = new ArrayList<>();
        LOGGER.info("List of available kex algorithms : {}",
                factories.stream().map(NamedResource::getName).collect(joining(",")));

        for (String name : names) {
            name = name.trim();
            boolean found = false;
            for (KeyExchangeFactory factory : factories) {
                if (factory.getName().equals(name)) {
                    list.add(factory);
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOGGER.warn("Configured KeyExchangeFactory '{}' not available", name);
            }
        }
        return list;
    }

    public static void configureMacs(String names, AbstractFactoryManager factoryManager) {
        if (isBlank(names)) {
            return;
        }
        Set<BuiltinMacs> builtIn = BuiltinMacs.VALUES;
        Set<MacFactory> registered = BuiltinMacs.getRegisteredExtensions();
        Set<MacFactory> allMacFactories = new HashSet<>();
        allMacFactories.addAll(builtIn);
        allMacFactories.addAll(registered);
        List<NamedFactory<Mac>> avail = (List) NamedFactory.setUpBuiltinFactories(false, allMacFactories);
        factoryManager.setMacFactories(filter(Mac.class, avail, names.split(",")));
    }

    public static void configureCiphers(String names, AbstractFactoryManager factoryManager) {
        if (isBlank(names)) {
            return;
        }
        Set<BuiltinCiphers> builtIn = BuiltinCiphers.VALUES;
        Set<CipherFactory> registered = BuiltinCiphers.getRegisteredExtensions();
        Set<CipherFactory> allCipherFactories = new HashSet<>();
        allCipherFactories.addAll(builtIn);
        allCipherFactories.addAll(registered);
        List<NamedFactory<Cipher>> avail = (List) NamedFactory.setUpBuiltinFactories(false, allCipherFactories);
        factoryManager.setCipherFactories(filter(Cipher.class, avail, names.split(",")));
    }

    public static void configureKexAlgorithms(String names, AbstractFactoryManager factoryManager) {
        if (isBlank(names)) {
            return;
        }
        Set<BuiltinDHFactories> builtin = BuiltinDHFactories.VALUES;
        NavigableSet<DHFactory> dhFactories = BuiltinDHFactories.getRegisteredExtensions();
        Set<DHFactory> allDHFactories = new HashSet<>();
        allDHFactories.addAll(builtin);
        allDHFactories.addAll(dhFactories);
        List<KeyExchangeFactory> avail = NamedFactory.setUpTransformedFactories(false, builtin, ClientBuilder.DH2KEX);
        factoryManager.setKeyExchangeFactories(filter(avail, names.split(",")));
    }

    public static void configureSignatureAlgorithms(String names, AbstractFactoryManager factoryManager) {
        if (isBlank(names)) {
            return;
        }
        Set<BuiltinSignatures> builtIn = BuiltinSignatures.VALUES;
        Set<SignatureFactory> registered = BuiltinSignatures.getRegisteredExtensions();
        Set<SignatureFactory> allSignatureFactories = new HashSet<>();
        allSignatureFactories.addAll(builtIn);
        allSignatureFactories.addAll(registered);
        List<NamedFactory<Signature>> avail = (List) NamedFactory.setUpBuiltinFactories(false, allSignatureFactories);
        factoryManager.setSignatureFactories(filter(Signature.class, avail, names.split(",")));
    }

    public static void configureCompressions(String names, AbstractFactoryManager factoryManager) {
        if (isBlank(names)) {
            return;
        }
        Set<BuiltinCompressions> builtIn = BuiltinCompressions.VALUES;
        Set<CompressionFactory> registered = BuiltinCompressions.getRegisteredExtensions();
        Set<CompressionFactory> allCompressionFactories = new HashSet<>();
        allCompressionFactories.addAll(builtIn);
        allCompressionFactories.addAll(registered);
        List<NamedFactory<Compression>> avail = (List) NamedFactory.setUpBuiltinFactories(false, allCompressionFactories);
        factoryManager.setCompressionFactories(filter(Compression.class, avail, names.split(",")));
    }

    public static void configureAlgorithms(SshConfiguration configuration, SshClient client) {
        configureCiphers(configuration.getCiphers(), client);
        configureKexAlgorithms(configuration.getKex(), client);
        configureSignatureAlgorithms(configuration.getSignatures(), client);
        configureMacs(configuration.getMacs(), client);
        configureCompressions(configuration.getCompressions(), client);
    }

}
