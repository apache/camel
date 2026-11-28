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
import java.security.Provider;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.apache.camel.component.pqc.spi.Bc186PqcAdapter;
import org.apache.camel.component.pqc.spi.Bc187PqcAdapter;
import org.apache.camel.component.pqc.spi.PqcBouncyCastleAdapter;

/** Selects a typed, version-specific Bouncy Castle adapter using the installed provider version. */
public final class BouncyCastlePqcAdapterSupport {

    private BouncyCastlePqcAdapterSupport() {
    }

    public static PqcBouncyCastleAdapter adapter() {
        final Provider provider;
        try {
            provider = BouncyCastleSupport.getProvider("KeyPairGenerator", "LMS");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot determine the installed Bouncy Castle version", e);
        }

        String version = provider.getVersionStr();
        ClassLoader providerLoader = provider.getClass().getClassLoader();
        if (version.equals("1.86") || version.startsWith("1.86.")) {
            return load(Bc186PqcAdapter.class, "1.86", providerLoader);
        }
        if (version.equals("1.87") || version.startsWith("1.87.")) {
            return load(Bc187PqcAdapter.class, "1.87", providerLoader);
        }
        throw new IllegalStateException(
                "camel-pqc has typed compatibility adapters for Bouncy Castle 1.86 and 1.87; "
                                        + "the installed provider is " + provider.getName() + " " + version);
    }

    private static <T extends PqcBouncyCastleAdapter> T load(
            Class<T> serviceType, String expectedVersion, ClassLoader providerLoader) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader componentLoader = BouncyCastlePqcAdapterSupport.class.getClassLoader();
        ClassLoader[] loaders = { providerLoader, contextLoader, componentLoader };
        Throwable firstFailure = null;

        for (int i = 0; i < loaders.length; i++) {
            ClassLoader loader = loaders[i];
            if (loader == null || alreadyTried(loaders, i, loader)) {
                continue;
            }
            try {
                var adapter = ServiceLoader.load(serviceType, loader).findFirst();
                if (adapter.isPresent()) {
                    return verifyVersion(adapter.get(), expectedVersion);
                }
            } catch (ServiceConfigurationError | LinkageError e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }

        IllegalStateException unavailable = new IllegalStateException(
                "No ServiceLoader implementation for Bouncy Castle " + expectedVersion + " was found. "
                                                                      + "Ensure camel-pqc-bc-compat-"
                                                                      + expectedVersion.replace(".", "")
                                                                      + " is present at runtime.");
        if (firstFailure != null) {
            unavailable.initCause(firstFailure);
        }
        throw unavailable;
    }

    private static boolean alreadyTried(ClassLoader[] loaders, int before, ClassLoader candidate) {
        for (int i = 0; i < before; i++) {
            if (loaders[i] == candidate) {
                return true;
            }
        }
        return false;
    }

    private static <T extends PqcBouncyCastleAdapter> T verifyVersion(T adapter, String expectedVersion) {
        if (!expectedVersion.equals(adapter.bcVersion())) {
            throw new IllegalStateException(
                    "Loaded Bouncy Castle adapter " + adapter.getClass().getName()
                                            + " targets " + adapter.bcVersion() + " but runtime requires "
                                            + expectedVersion);
        }
        return adapter;
    }
}
