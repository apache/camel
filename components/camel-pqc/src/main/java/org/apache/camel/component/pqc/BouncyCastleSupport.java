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
package org.apache.camel.component.pqc;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Central access point for Bouncy Castle providers.
 *
 * <p>
 * Provider selection is service-specific. A provider that implements a KeyPairGenerator is not necessarily the provider
 * that implements the matching Signature, KeyGenerator, KeyFactory, or Cipher service.
 * </p>
 *
 * <p>
 * The provider implementations are loaded reflectively so the rest of the component does not have to link directly to
 * provider implementation classes. An already installed provider is never replaced or removed; runtimes such as Quarkus
 * can therefore register their BOM-selected provider instances before Camel starts.
 * </p>
 */
public final class BouncyCastleSupport {

    private static final Object PROVIDER_REGISTRATION_LOCK = new Object();

    private static final String BC_NAME = "BC";
    private static final String BCPQC_NAME = "BCPQC";
    private static final String BC_CLASS = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    private static final String BCPQC_CLASS = "org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider";

    private BouncyCastleSupport() {
    }

    /**
     * Installs whichever BC provider implementations are available on the runtime class path. An unavailable optional
     * provider implementation does not prevent use of the other provider.
     */
    public static void ensureProviders() throws GeneralSecurityException {
        GeneralSecurityException failure = null;
        try {
            ensureProvider(BC_NAME, BC_CLASS);
        } catch (GeneralSecurityException e) {
            failure = e;
        }
        try {
            ensureProvider(BCPQC_NAME, BCPQC_CLASS);
        } catch (GeneralSecurityException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }

        if (Security.getProvider(BC_NAME) == null && Security.getProvider(BCPQC_NAME) == null) {
            GeneralSecurityException unavailable = new GeneralSecurityException(
                    "Neither Bouncy Castle provider (BC or BCPQC) could be installed. "
                                                                                + "Check the runtime Bouncy Castle dependencies and class-loader visibility.");
            if (failure != null) {
                unavailable.initCause(failure);
            }
            throw unavailable;
        }
    }

    /** Returns the BC/BCPQC provider which advertises the requested JCA service and algorithm. */
    public static Provider getProvider(String serviceType, String algorithm) throws GeneralSecurityException {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(algorithm, "algorithm");
        Provider provider = findProvider(serviceType, algorithm,
                Security.getProvider(BC_NAME), Security.getProvider(BCPQC_NAME));
        if (provider != null) {
            return provider;
        }

        // Do not register either provider as a side effect when an embedding runtime has already installed
        // the provider that supplies this exact JCA service. Install missing providers only when needed.
        ensureProviders();
        provider = findProvider(serviceType, algorithm,
                Security.getProvider(BC_NAME), Security.getProvider(BCPQC_NAME));
        if (provider != null) {
            return provider;
        }

        List<String> installed = new ArrayList<>();
        describeProvider(installed, Security.getProvider(BC_NAME), serviceType, algorithm);
        describeProvider(installed, Security.getProvider(BCPQC_NAME), serviceType, algorithm);
        throw new NoSuchAlgorithmException(
                "No Bouncy Castle provider advertises JCA service '" + serviceType
                                           + "' for algorithm '" + algorithm + "'. Checked providers in order BC, BCPQC: "
                                           + installed
                                           + ". The runtime Bouncy Castle version may not support this algorithm/service combination.");
    }

    /** Returns the provider name for APIs which still require a provider-name overload. */
    public static String getProviderName(String serviceType, String algorithm) throws GeneralSecurityException {
        return getProvider(serviceType, algorithm).getName();
    }

    /**
     * Package-visible seam used to test service-specific selection without global provider mutation. Cipher providers
     * advertise base algorithms (for example AES), not complete transformations (for example AES/GCM/NoPadding). JCA
     * applies the mode and padding when Cipher.getInstance receives the full transformation, so provider discovery must
     * check only the base algorithm.
     */
    static String serviceLookupAlgorithm(String serviceType, String algorithm) {
        if ("Cipher".equalsIgnoreCase(serviceType)) {
            int separator = algorithm.indexOf('/');
            if (separator > 0) {
                return algorithm.substring(0, separator);
            }
        }
        return algorithm;
    }

    static Provider findProvider(String serviceType, String algorithm, Provider... providers) {
        String lookupAlgorithm = serviceLookupAlgorithm(serviceType, algorithm);
        for (Provider provider : providers) {
            if (provider != null && provider.getService(serviceType, lookupAlgorithm) != null) {
                return provider;
            }
        }
        return null;
    }

    private static void describeProvider(List<String> result, Provider provider, String serviceType, String algorithm) {
        if (provider == null) {
            result.add("not installed");
        } else {
            result.add(provider.getName() + " " + provider.getVersionStr() + " (service present: "
                       + (provider.getService(serviceType, serviceLookupAlgorithm(serviceType, algorithm)) != null) + ")");
        }
    }

    private static void ensureProvider(String providerName, String providerClassName) throws GeneralSecurityException {
        if (Security.getProvider(providerName) != null) {
            return;
        }
        synchronized (PROVIDER_REGISTRATION_LOCK) {
            if (Security.getProvider(providerName) != null) {
                return;
            }
            Provider candidate = instantiateProvider(providerClassName);
            if (!providerName.equals(candidate.getName())) {
                throw new GeneralSecurityException(
                        "Provider class " + providerClassName + " returned provider '"
                                                   + candidate.getName() + "', expected '" + providerName + "'.");
            }
            Security.addProvider(candidate);
            // Another thread or security manager may have won the registration race.
            if (Security.getProvider(providerName) == null) {
                throw new GeneralSecurityException("Could not register Bouncy Castle provider '" + providerName + "'.");
            }
        }
    }

    private static Provider instantiateProvider(String className) throws GeneralSecurityException {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader ownLoader = BouncyCastleSupport.class.getClassLoader();
        Throwable firstFailure = null;
        for (ClassLoader loader : new ClassLoader[] { contextLoader, ownLoader }) {
            if (loader == null || (firstFailure != null && loader == contextLoader && contextLoader == ownLoader)) {
                continue;
            }
            try {
                Class<?> providerClass = Class.forName(className, true, loader);
                Object instance = providerClass.getDeclaredConstructor().newInstance();
                if (!(instance instanceof Provider provider)) {
                    throw new GeneralSecurityException("Class " + className + " is not a java.security.Provider.");
                }
                return provider;
            } catch (ReflectiveOperationException | LinkageError e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        throw new GeneralSecurityException(
                "Could not load Bouncy Castle provider implementation " + className
                                           + " from the thread context or component class loader.",
                firstFailure);
    }
}
