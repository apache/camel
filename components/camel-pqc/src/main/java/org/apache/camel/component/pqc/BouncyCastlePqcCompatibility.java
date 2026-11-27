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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;

/**
 * Isolates the LMS/HSS and XMSS/XMSSMT JCA API package changes between Bouncy Castle releases.
 *
 * <p>
 * BC 1.86 exposes these parameter specs and stateful-key interfaces in the legacy {@code org.bouncycastle.pqc.jcajce}
 * packages. BC 1.87 also exposes the current {@code org.bouncycastle.jcajce} types and deprecates the legacy aliases.
 * This class keeps those version-specific names out of the component's compile-time dependencies. It prefers the
 * current API and falls back to the legacy API only when the current type is unavailable.
 * </p>
 *
 * <p>
 * Reflection is deliberately limited to this compatibility boundary. Runtime BC classes are resolved with the
 * application context class loader first, then the component class loader, so a platform BOM can supply the runtime
 * implementation. The adapter does not retain resolved BC classes in global caches. Stateful-key accessors use
 * {@link ClassValue} so their lifetime follows the key class. Callers use ordinary {@link AlgorithmParameterSpec} and
 * {@link PrivateKey} types.
 * </p>
 */
public final class BouncyCastlePqcCompatibility {

    private static final String CURRENT_SPEC_PACKAGE = "org.bouncycastle.jcajce.spec.";
    private static final String LEGACY_SPEC_PACKAGE = "org.bouncycastle.pqc.jcajce.spec.";
    private static final String CURRENT_INTERFACE_PACKAGE = "org.bouncycastle.jcajce.interfaces.";
    private static final String LEGACY_INTERFACE_PACKAGE = "org.bouncycastle.pqc.jcajce.interfaces.";

    private static final StatefulKeyAccessors NOT_STATEFUL = new StatefulKeyAccessors(null, null);

    private static final ClassValue<StatefulKeyAccessors> STATEFUL_KEY_ACCESSORS = new ClassValue<>() {
        @Override
        protected StatefulKeyAccessors computeValue(Class<?> keyClass) {
            Class<?> keyInterface = findStatefulKeyInterface(keyClass);
            if (keyInterface == null) {
                return NOT_STATEFUL;
            }
            try {
                return new StatefulKeyAccessors(
                        keyInterface.getMethod("getIndex"), keyInterface.getMethod("getUsagesRemaining"));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "Bouncy Castle stateful-key interface " + keyInterface.getName()
                                                + " does not expose the expected index/remaining methods",
                        e);
            }
        }
    };

    private BouncyCastlePqcCompatibility() {
    }

    /** Resolves a named BC parameter spec without linking callers to its implementation class. */
    public static AlgorithmParameterSpec namedParameterSpec(String simpleName, String parameterName)
            throws GeneralSecurityException {
        if (simpleName == null || simpleName.isBlank() || parameterName == null || parameterName.isBlank()) {
            throw new IllegalArgumentException("Bouncy Castle parameter-spec type and name must be non-blank");
        }
        Class<?> specType = resolveSpecType(simpleName, keyPairGeneratorAlgorithm(simpleName));
        Method resolver = resolveSpecNameResolver(specType);
        try {
            Object result = resolver.invoke(null, parameterName);
            if (result == null) {
                return null;
            }
            if (!(result instanceof AlgorithmParameterSpec parameterSpec)) {
                throw new GeneralSecurityException(
                        specType.getName() + ".fromName(String) did not return AlgorithmParameterSpec");
            }
            return parameterSpec;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new GeneralSecurityException(
                    "Bouncy Castle failed to resolve parameter set '" + parameterName + "' using " + specType.getName(),
                    cause);
        } catch (ReflectiveOperationException e) {
            throw new GeneralSecurityException(
                    "Could not invoke Bouncy Castle parameter resolver " + specType.getName() + ".fromName(String)", e);
        }
    }

    /** Creates an LMS key-generation parameter spec using the API available at runtime. */
    public static AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureParameterName, String otsParameterName)
            throws GeneralSecurityException {
        Class<?> signatureType = resolveLmsParameterType("LMSigParameters", "LMS");
        Class<?> otsType = resolveLmsParameterType("LMOtsParameters", "LMS");
        Object signatureParameters = readPublicStaticParameter(signatureType, signatureParameterName);
        Object otsParameters = readPublicStaticParameter(otsType, otsParameterName);
        Class<?> specType = resolveSpecType("LMSKeyGenParameterSpec", "LMS");
        return createSpec(specType, new Class<?>[] { signatureType, otsType }, signatureParameters, otsParameters);
    }

    /** Creates an HSS key-generation parameter spec using the API available at runtime. */
    public static AlgorithmParameterSpec lmsHssKeyGenParameterSpec(
            String[] signatureParameterNames, String[] otsParameterNames)
            throws GeneralSecurityException {
        if (signatureParameterNames == null || otsParameterNames == null || signatureParameterNames.length == 0
                || signatureParameterNames.length != otsParameterNames.length) {
            throw new IllegalArgumentException("HSS requires matching, non-empty LMS and LM-OTS parameter arrays");
        }

        Class<?> lmsSpecType = resolveSpecType("LMSKeyGenParameterSpec", "LMS");
        Object lmsSpecs = Array.newInstance(lmsSpecType, signatureParameterNames.length);
        for (int i = 0; i < signatureParameterNames.length; i++) {
            Array.set(lmsSpecs, i, lmsKeyGenParameterSpec(signatureParameterNames[i], otsParameterNames[i]));
        }

        Class<?> hssSpecType = resolveSpecType("LMSHSSKeyGenParameterSpec", "HSS");
        return createSpec(hssSpecType, new Class<?>[] { lmsSpecs.getClass() }, lmsSpecs);
    }

    /** Creates an XMSS parameter spec using the API available at runtime. */
    public static AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest)
            throws GeneralSecurityException {
        Class<?> specType = resolveSpecType("XMSSParameterSpec", "XMSS");
        return createSpec(specType, new Class<?>[] { int.class, String.class }, height, treeDigest);
    }

    /** Creates an XMSSMT parameter spec using the API available at runtime. */
    public static AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest)
            throws GeneralSecurityException {
        Class<?> specType = resolveSpecType("XMSSMTParameterSpec", "XMSSMT");
        return createSpec(specType, new Class<?>[] { int.class, int.class, String.class }, height, layers, treeDigest);
    }

    /**
     * Returns the number of signatures remaining for a stateful private key, or {@code -1} when the key is not a
     * recognized LMS/HSS/XMSS/XMSSMT private key.
     */
    public static long getStatefulKeyRemaining(PrivateKey privateKey) {
        StatefulKeyAccessors accessors = statefulKeyAccessors(privateKey);
        return accessors == NOT_STATEFUL ? -1 : invokeLong(privateKey, accessors.remainingMethod, "getUsagesRemaining");
    }

    /**
     * Returns the current signature index for a stateful private key, or {@code 0} when the key is not a recognized
     * LMS/HSS/XMSS/XMSSMT private key.
     */
    public static long getStatefulKeyIndex(PrivateKey privateKey) {
        StatefulKeyAccessors accessors = statefulKeyAccessors(privateKey);
        return accessors == NOT_STATEFUL ? 0 : invokeLong(privateKey, accessors.indexMethod, "getIndex");
    }

    private static StatefulKeyAccessors statefulKeyAccessors(PrivateKey privateKey) {
        return privateKey == null ? NOT_STATEFUL : STATEFUL_KEY_ACCESSORS.get(privateKey.getClass());
    }

    private static Class<?> findStatefulKeyInterface(Class<?> keyClass) {
        String[] names = {
                CURRENT_INTERFACE_PACKAGE + "XMSSPrivateKey",
                CURRENT_INTERFACE_PACKAGE + "XMSSMTPrivateKey",
                CURRENT_INTERFACE_PACKAGE + "LMSPrivateKey",
                LEGACY_INTERFACE_PACKAGE + "XMSSPrivateKey",
                LEGACY_INTERFACE_PACKAGE + "XMSSMTPrivateKey",
                LEGACY_INTERFACE_PACKAGE + "LMSPrivateKey"
        };
        ClassLoader loader = keyClass.getClassLoader();
        for (String name : names) {
            try {
                Class<?> candidate = Class.forName(name, false, loader);
                if (candidate.isAssignableFrom(keyClass)) {
                    return candidate;
                }
            } catch (ClassNotFoundException ignored) {
                // Try the other package name supported by the runtime BC version.
            }
        }
        return null;
    }

    private static long invokeLong(PrivateKey privateKey, Method method, String propertyName) {
        try {
            return ((Number) method.invoke(privateKey)).longValue();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Could not read stateful Bouncy Castle key property '" + propertyName + "'", cause);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException(
                    "Could not read stateful Bouncy Castle key property '" + propertyName + "'", e);
        }
    }

    private static Class<?> resolveSpecType(String simpleName, String keyPairGeneratorAlgorithm)
            throws GeneralSecurityException {
        ClassNotFoundException firstFailure = null;
        for (String packageName : new String[] { CURRENT_SPEC_PACKAGE, LEGACY_SPEC_PACKAGE }) {
            String className = packageName + simpleName;
            try {
                return loadSpecType(className, keyPairGeneratorAlgorithm);
            } catch (ClassNotFoundException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        throw new GeneralSecurityException(
                "The runtime Bouncy Castle version does not provide " + simpleName
                                           + " in either supported package " + CURRENT_SPEC_PACKAGE + " or "
                                           + LEGACY_SPEC_PACKAGE,
                firstFailure);
    }

    /** Maps public spec names to the JCA service whose provider must own the returned parameter-spec class. */
    private static String keyPairGeneratorAlgorithm(String simpleName) {
        switch (simpleName) {
            case "MLDSAParameterSpec":
                return "ML-DSA";
            case "SLHDSAParameterSpec":
                return "SLH-DSA";
            case "FalconParameterSpec":
                return "FALCON";
            case "MayoParameterSpec":
                return "MAYO";
            case "SnovaParameterSpec":
                return "SNOVA";
            case "MLKEMParameterSpec":
                return "ML-KEM";
            case "NTRUParameterSpec":
                return "NTRU";
            case "NTRULPRimeParameterSpec":
                return "NTRULPRIME";
            case "SNTRUPrimeParameterSpec":
                return "SNTRUPRIME";
            case "BIKEParameterSpec":
                return "BIKE";
            case "HQCParameterSpec":
                return "HQC";
            case "CMCEParameterSpec":
                return "CMCE";
            case "FrodoKEMParameterSpec":
                return "FrodoKEM";
            case "SABERParameterSpec":
                return "SABER";
            case "LMSKeyGenParameterSpec":
                return "LMS";
            case "LMSHSSKeyGenParameterSpec":
                return "HSS";
            case "XMSSParameterSpec":
                return "XMSS";
            case "XMSSMTParameterSpec":
                return "XMSSMT";
            default:
                return null;
        }
    }

    private static Method resolveSpecNameResolver(Class<?> specType) throws GeneralSecurityException {
        try {
            Method resolved = specType.getMethod("fromName", String.class);
            if (!java.lang.reflect.Modifier.isStatic(resolved.getModifiers())) {
                throw new GeneralSecurityException(
                        "Bouncy Castle parameter resolver " + specType.getName() + ".fromName(String) is not static");
            }
            return resolved;
        } catch (NoSuchMethodException e) {
            throw new GeneralSecurityException(
                    "Bouncy Castle parameter spec " + specType.getName()
                                               + " does not expose the expected public fromName(String) method",
                    e);
        }
    }

    private static Class<?> resolveLmsParameterType(String simpleName, String keyPairGeneratorAlgorithm)
            throws GeneralSecurityException {
        ClassNotFoundException failure = null;
        for (String packageName : new String[] {
                "org.bouncycastle.crypto.params.",
                "org.bouncycastle.pqc.crypto.lms."
        }) {
            try {
                return loadRuntimeClass(packageName + simpleName, keyPairGeneratorAlgorithm);
            } catch (ClassNotFoundException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        throw new GeneralSecurityException(
                "The runtime Bouncy Castle version does not provide LMS parameter type " + simpleName, failure);
    }

    private static Object readPublicStaticParameter(Class<?> parameterType, String fieldName)
            throws GeneralSecurityException {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("Bouncy Castle LMS parameter field name must be non-blank");
        }
        try {
            java.lang.reflect.Field field = parameterType.getField(fieldName);
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                throw new GeneralSecurityException(
                        "Bouncy Castle LMS parameter " + parameterType.getName() + "." + fieldName + " is not static");
            }
            Object value = field.get(null);
            if (value == null) {
                throw new GeneralSecurityException(
                        "Bouncy Castle LMS parameter " + parameterType.getName() + "." + fieldName + " is null");
            }
            return value;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new GeneralSecurityException(
                    "Bouncy Castle LMS parameter " + parameterType.getName() + "." + fieldName + " is unavailable", e);
        }
    }

    private static ClassLoader[] compatibilityClassLoaders() {
        return compatibilityClassLoaders(null, Thread.currentThread().getContextClassLoader(),
                BouncyCastlePqcCompatibility.class.getClassLoader());
    }

    private static ClassLoader[] compatibilityClassLoaders(String keyPairGeneratorAlgorithm) {
        ClassLoader providerLoader = null;
        if (keyPairGeneratorAlgorithm != null) {
            try {
                providerLoader = BouncyCastleSupport.getProvider("KeyPairGenerator", keyPairGeneratorAlgorithm)
                        .getClass().getClassLoader();
            } catch (GeneralSecurityException ignored) {
                // The context/component fallback below preserves the existing diagnostic path if no provider is ready.
            }
        }
        return compatibilityClassLoaders(providerLoader, Thread.currentThread().getContextClassLoader(),
                BouncyCastlePqcCompatibility.class.getClassLoader());
    }

    /** Test seam; the selected provider loader is deliberately tried before a context loader which may shadow BC. */
    static ClassLoader[] compatibilityClassLoaders(ClassLoader contextLoader, ClassLoader componentLoader) {
        return compatibilityClassLoaders(null, contextLoader, componentLoader);
    }

    static ClassLoader[] compatibilityClassLoaders(
            ClassLoader providerLoader, ClassLoader contextLoader, ClassLoader componentLoader) {
        java.util.ArrayList<ClassLoader> result = new java.util.ArrayList<>(3);
        addLoader(result, providerLoader);
        addLoader(result, contextLoader);
        addLoader(result, componentLoader);
        return result.toArray(new ClassLoader[0]);
    }

    private static void addLoader(java.util.List<ClassLoader> loaders, ClassLoader candidate) {
        if (candidate != null && !loaders.contains(candidate)) {
            loaders.add(candidate);
        }
    }

    private static Class<?> loadRuntimeClass(String className, String keyPairGeneratorAlgorithm)
            throws ClassNotFoundException {
        return loadWithCompatibilityLoaders(className, compatibilityClassLoaders(keyPairGeneratorAlgorithm));
    }

    private static Class<?> loadSpecType(String className, String keyPairGeneratorAlgorithm)
            throws ClassNotFoundException {
        // Do not cache by class name alone: different Camel application contexts can
        // resolve different BC artifacts through their own runtime class loaders.
        return loadWithCompatibilityLoaders(className, compatibilityClassLoaders(keyPairGeneratorAlgorithm));
    }

    private static Class<?> loadWithCompatibilityLoaders(String className, ClassLoader[] loaders)
            throws ClassNotFoundException {
        ClassNotFoundException firstFailure = null;
        for (ClassLoader loader : loaders) {
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                } else {
                    firstFailure.addSuppressed(e);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        return Class.forName(className);
    }

    private static AlgorithmParameterSpec createSpec(
            Class<?> specType, Class<?>[] parameterTypes, Object... arguments)
            throws GeneralSecurityException {
        Constructor<?> constructor = resolveConstructor(specType, parameterTypes);
        try {
            Object result = constructor.newInstance(arguments);
            if (!(result instanceof AlgorithmParameterSpec parameterSpec)) {
                throw new GeneralSecurityException(specType.getName() + " does not implement AlgorithmParameterSpec");
            }
            return parameterSpec;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new GeneralSecurityException(
                    "Could not construct Bouncy Castle parameter spec " + specType.getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new GeneralSecurityException(
                    "Could not construct Bouncy Castle parameter spec " + specType.getName(), e);
        }
    }

    private static Constructor<?> resolveConstructor(Class<?> specType, Class<?>[] parameterTypes)
            throws GeneralSecurityException {
        try {
            return specType.getConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new GeneralSecurityException(
                    "Bouncy Castle parameter spec " + specType.getName()
                                               + " does not expose the expected public constructor",
                    e);
        }
    }

    private static final class StatefulKeyAccessors {
        private final Method indexMethod;
        private final Method remainingMethod;

        private StatefulKeyAccessors(Method indexMethod, Method remainingMethod) {
            this.indexMethod = indexMethod;
            this.remainingMethod = remainingMethod;
        }
    }
}
