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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the default-material holders are lazy implementation details. The test classloader blocks the provider
 * implementation classes while initializing each holder. A regression to a provider-constructing static initializer
 * would then fail with ExceptionInInitializerError / linkage error.
 */
class PQCDefaultMaterialCompatibilityTest {

    private static final List<String> DEFAULT_MATERIAL_CLASSES = List.of(
            "org.apache.camel.component.pqc.crypto.PQCDefaultDILITHIUMMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultFalconMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultHSSMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultLMSMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultMAYOMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultMLDSAMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultSLHDSAMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultSNOVAMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultSPHINCSPLUSMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultXMSSMTMaterial",
            "org.apache.camel.component.pqc.crypto.PQCDefaultXMSSMaterial",
            "org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultECDHMLKEMMaterial",
            "org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultECDSAMLDSAMaterial",
            "org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultEd25519MLDSAMaterial",
            "org.apache.camel.component.pqc.crypto.hybrid.PQCDefaultX25519MLKEMMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultBIKEMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultCMCEMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultFRODOMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultHQCMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultKYBERMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultMLKEMMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRULPRimeMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultNTRUMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultSABERMaterial",
            "org.apache.camel.component.pqc.crypto.kem.PQCDefaultSNTRUPrimeMaterial");

    @Test
    void allDefaultMaterialHoldersInitializeWithoutLoadingProviderImplementationClasses() throws Exception {
        for (String className : DEFAULT_MATERIAL_CLASSES) {
            ClassLoader isolated = providerBlockingClassLoader(className);
            Class<?> loaded = Class.forName(className, true, isolated);
            assertNotNull(loaded, "Default material class should initialize without eagerly loading BC providers: "
                                  + className);
        }
    }

    /**
     * These holder types are not part of the public component/endpoint/configuration API. This assertion makes that
     * boundary explicit: callers configure camel-pqc through its component, endpoint, configuration and data format,
     * not through the implementation's default-material holder classes.
     */
    @Test
    void defaultMaterialHolderTypesAreNotExposedByTheComponentApi() {
        for (Class<?> apiType : List.of(PQCComponent.class, PQCEndpoint.class, PQCConfiguration.class,
                org.apache.camel.component.pqc.dataformat.PQCDataFormat.class)) {
            for (Field field : apiType.getFields()) {
                assertFalse(isDefaultMaterialType(field.getType()),
                        () -> apiType.getName() + " exposes default material type through field " + field.getName());
            }
            for (Method method : apiType.getMethods()) {
                assertFalse(isDefaultMaterialType(method.getReturnType()),
                        () -> apiType.getName() + " exposes default material type through method " + method.getName());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    assertFalse(isDefaultMaterialType(parameterType),
                            () -> apiType.getName() + " exposes default material type through method " + method.getName());
                }
            }
        }

        // The fields that triggered eager class initialization are intentionally no longer public API.
        for (String className : DEFAULT_MATERIAL_CLASSES) {
            try {
                Class<?> holder = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                for (Field field : holder.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                        assertFalse(Modifier.isPublic(field.getModifiers()),
                                () -> className + " unexpectedly exposes a public static implementation field: "
                                      + field.getName());
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Default-material implementation class is missing: " + className, e);
            }
        }
    }

    private static boolean isDefaultMaterialType(Class<?> type) {
        if (type.isArray()) {
            return isDefaultMaterialType(type.getComponentType());
        }
        return type.getName().startsWith("org.apache.camel.component.pqc.crypto.PQCDefault")
                || type.getName().startsWith("org.apache.camel.component.pqc.crypto.hybrid.PQCDefault")
                || type.getName().startsWith("org.apache.camel.component.pqc.crypto.kem.PQCDefault");
    }

    private static ClassLoader providerBlockingClassLoader(String targetClassName) {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        String supportClassName = "org.apache.camel.component.pqc.BouncyCastleSupport";
        return new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("org.bouncycastle.jce.provider.BouncyCastleProvider")
                        || name.equals("org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider")) {
                    throw new ClassNotFoundException("BC providers intentionally hidden for lazy-init test: " + name);
                }
                // Load the holder and provider helper in this filtered loader. This also catches a static call to
                // BouncyCastleSupport.ensureProviders(), rather than accidentally delegating that call to the parent.
                if (!name.equals(targetClassName) && !name.equals(supportClassName)) {
                    return super.loadClass(name, resolve);
                }

                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) {
                        String resourceName = name.replace('.', '/') + ".class";
                        try (InputStream input = parent.getResourceAsStream(resourceName)) {
                            if (input == null) {
                                throw new ClassNotFoundException("Cannot read class resource " + resourceName);
                            }
                            ByteArrayOutputStream output = new ByteArrayOutputStream();
                            input.transferTo(output);
                            byte[] bytes = output.toByteArray();
                            result = defineClass(name, bytes, 0, bytes.length);
                        } catch (Exception e) {
                            throw new ClassNotFoundException("Cannot define isolated class " + name, e);
                        }
                    }
                    if (resolve) {
                        resolveClass(result);
                    }
                    return result;
                }
            }
        };
    }
}
