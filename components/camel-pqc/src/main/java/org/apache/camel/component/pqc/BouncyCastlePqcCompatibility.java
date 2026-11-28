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

import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Locale;
import java.util.Set;

import org.bouncycastle.jcajce.spec.FrodoKEMParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jcajce.spec.SLHDSAParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.BIKEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.HQCParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.MayoParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SABERParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SNTRUPrimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SnovaParameterSpec;

/** Typed access to the public Bouncy Castle JCA parameter-set APIs. */
public final class BouncyCastlePqcCompatibility {

    private BouncyCastlePqcCompatibility() {
    }

    private static final Set<String> ML_DSA_PARAMETER_SETS = Set.of(
            "ML-DSA-44", "ML-DSA-65", "ML-DSA-87",
            "ML-DSA-44-WITH-SHA512", "ML-DSA-65-WITH-SHA512", "ML-DSA-87-WITH-SHA512");
    private static final Set<String> ML_KEM_PARAMETER_SETS = Set.of(
            "ML-KEM-512", "ML-KEM-768", "ML-KEM-1024");
    private static final Set<String> CMCE_PARAMETER_SETS = Set.of(
            "mceliece460896", "mceliece460896f", "mceliece460896pc", "mceliece460896pcf",
            "mceliece6688128", "mceliece6688128f", "mceliece6688128pc", "mceliece6688128pcf",
            "mceliece6960119", "mceliece6960119f", "mceliece6960119pc", "mceliece6960119pcf",
            "mceliece8192128", "mceliece8192128f", "mceliece8192128pc", "mceliece8192128pcf");

    public static AlgorithmParameterSpec resolveParameterSpec(String algorithm, String parameterName) {
        if (algorithm == null || parameterName == null || parameterName.isBlank()) {
            throw new IllegalArgumentException("Algorithm and parameter-set name must be non-blank");
        }
        return switch (algorithm.toUpperCase(Locale.ROOT)) {
            case "MLDSA", "DILITHIUM" -> namedParameter(parameterName, ML_DSA_PARAMETER_SETS);
            case "SLHDSA", "SPHINCSPLUS" -> SLHDSAParameterSpec.fromName(parameterName);
            case "FALCON" -> FalconParameterSpec.fromName(parameterName);
            case "MAYO" -> MayoParameterSpec.fromName(parameterName);
            case "SNOVA" -> SnovaParameterSpec.fromName(parameterName);
            case "MLKEM" -> namedParameter(parameterName, ML_KEM_PARAMETER_SETS);
            case "KYBER" -> MLKEMParameterSpec.fromName(parameterName);
            case "NTRU" -> NTRUParameterSpec.fromName(parameterName);
            case "NTRULPRIME" -> NTRULPRimeParameterSpec.fromName(parameterName);
            case "SNTRUPRIME" -> SNTRUPrimeParameterSpec.fromName(parameterName);
            case "BIKE" -> BIKEParameterSpec.fromName(parameterName);
            case "HQC" -> HQCParameterSpec.fromName(parameterName);
            case "CMCE" -> cmceParameterSpec(parameterName);
            case "FRODO" -> FrodoKEMParameterSpec.fromName(parameterName);
            case "SABER" -> SABERParameterSpec.fromName(parameterName);
            default -> throw new IllegalArgumentException("No named parameter-set resolver for " + algorithm);
        };
    }

    private static AlgorithmParameterSpec namedParameter(String parameterName, Set<String> allowedNames) {
        String normalized = parameterName.replace('_', '-').toUpperCase(Locale.ROOT);
        if (!allowedNames.contains(normalized)) {
            throw new IllegalArgumentException("Unknown standardized parameter set: " + parameterName);
        }
        return new NamedParameterSpec(normalized);
    }

    private static AlgorithmParameterSpec cmceParameterSpec(String parameterName) {
        String normalized = parameterName.toLowerCase(Locale.ROOT);
        if (!CMCE_PARAMETER_SETS.contains(normalized)) {
            throw new IllegalArgumentException("Unknown CMCE parameter set: " + parameterName);
        }
        // NamedParameterSpec avoids the deprecated CMCEParameterSpec type in BC 1.87.
        return new NamedParameterSpec(normalized);
    }

    public static AlgorithmParameterSpec namedParameterSpec(String algorithm, String parameterName) {
        return resolveParameterSpec(algorithm, parameterName);
    }

    private enum StatefulApi {
        BC_187,
        BC_186,
        UNAVAILABLE
    }

    private static final class ApiHolder {
        private static final StatefulApi API = detectApi();

        private static StatefulApi detectApi() {
            try {
                Bc187Access.verifyAvailable();
                return StatefulApi.BC_187;
            } catch (LinkageError unavailable187) {
                try {
                    Bc186Access.verifyAvailable();
                    return StatefulApi.BC_186;
                } catch (LinkageError unavailable186) {
                    return StatefulApi.UNAVAILABLE;
                }
            }
        }
    }

    public static AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureName, String otsName) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.lmsKeyGenParameterSpec(signatureName, otsName);
            case BC_186 -> Bc186Access.lmsKeyGenParameterSpec(signatureName, otsName);
            case UNAVAILABLE -> throw unsupportedStatefulApi("LMS");
        };
    }

    public static AlgorithmParameterSpec lmsHssKeyGenParameterSpec(String[] signatureNames, String[] otsNames) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.lmsHssKeyGenParameterSpec(signatureNames, otsNames);
            case BC_186 -> Bc186Access.lmsHssKeyGenParameterSpec(signatureNames, otsNames);
            case UNAVAILABLE -> throw unsupportedStatefulApi("HSS");
        };
    }

    public static AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.xmssParameterSpec(height, treeDigest);
            case BC_186 -> Bc186Access.xmssParameterSpec(height, treeDigest);
            case UNAVAILABLE -> throw unsupportedStatefulApi("XMSS");
        };
    }

    public static AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.xmssMtParameterSpec(height, layers, treeDigest);
            case BC_186 -> Bc186Access.xmssMtParameterSpec(height, layers, treeDigest);
            case UNAVAILABLE -> throw unsupportedStatefulApi("XMSSMT");
        };
    }

    public static long getStatefulKeyRemaining(PrivateKey privateKey) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.getStatefulKeyRemaining(privateKey);
            case BC_186 -> Bc186Access.getStatefulKeyRemaining(privateKey);
            case UNAVAILABLE -> -1;
        };
    }

    public static long getStatefulKeyIndex(PrivateKey privateKey) {
        return switch (ApiHolder.API) {
            case BC_187 -> Bc187Access.getStatefulKeyIndex(privateKey);
            case BC_186 -> Bc186Access.getStatefulKeyIndex(privateKey);
            case UNAVAILABLE -> 0;
        };
    }

    private static IllegalStateException unsupportedStatefulApi(String algorithm) {
        return new IllegalStateException(
                "The installed Bouncy Castle runtime exposes neither the 1.87 nor 1.86 typed JCA API "
                                         + "required to configure " + algorithm + " stateful keys.");
    }

    /** Bouncy Castle 1.87+ package layout. Loaded only when stateful-key support is first requested. */
    private static final class Bc187Access {
        private static void verifyAvailable() {
            lmsKeyGenParameterSpec("lms_sha256_n32_h5", "sha256_n32_w1");
            lmsHssKeyGenParameterSpec(
                    new String[] { "lms_sha256_n32_h5", "lms_sha256_n32_h5" },
                    new String[] { "sha256_n32_w2", "sha256_n32_w2" });
            xmssParameterSpec(2, "SHA256");
            xmssMtParameterSpec(20, 2, "SHA256");
            isLmsPrivateKey(null);
            isXmssPrivateKey(null);
            isXmssMtPrivateKey(null);
        }

        private static boolean isLmsPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.jcajce.interfaces.LMSPrivateKey;
        }

        private static boolean isXmssPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSPrivateKey;
        }

        private static boolean isXmssMtPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSMTPrivateKey;
        }

        private static AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureName, String otsName) {
            return org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec.fromNames(
                    signatureName.replace('_', '-'), otsName.replace('_', '-'));
        }

        private static AlgorithmParameterSpec lmsHssKeyGenParameterSpec(String[] signatureNames, String[] otsNames) {
            if (signatureNames == null || otsNames == null || signatureNames.length == 0
                    || signatureNames.length != otsNames.length) {
                throw new IllegalArgumentException("HSS requires matching, non-empty LMS and LM-OTS parameter arrays");
            }
            org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec[] specs
                    = new org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec[signatureNames.length];
            for (int i = 0; i < specs.length; i++) {
                specs[i] = org.bouncycastle.jcajce.spec.LMSKeyGenParameterSpec.fromNames(
                        signatureNames[i].replace('_', '-'), otsNames[i].replace('_', '-'));
            }
            return new org.bouncycastle.jcajce.spec.LMSHSSKeyGenParameterSpec(specs);
        }

        private static AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest) {
            return new org.bouncycastle.jcajce.spec.XMSSParameterSpec(height, treeDigest);
        }

        private static AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest) {
            return new org.bouncycastle.jcajce.spec.XMSSMTParameterSpec(height, layers, treeDigest);
        }

        private static long getStatefulKeyRemaining(PrivateKey privateKey) {
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSPrivateKey xmss) {
                return xmss.getUsagesRemaining();
            }
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSMTPrivateKey xmssMt) {
                return xmssMt.getUsagesRemaining();
            }
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.LMSPrivateKey lms) {
                return lms.getUsagesRemaining();
            }
            return -1;
        }

        private static long getStatefulKeyIndex(PrivateKey privateKey) {
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSPrivateKey xmss) {
                return xmss.getIndex();
            }
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.XMSSMTPrivateKey xmssMt) {
                return xmssMt.getIndex();
            }
            if (privateKey instanceof org.bouncycastle.jcajce.interfaces.LMSPrivateKey lms) {
                return lms.getIndex();
            }
            return 0;
        }
    }

    /** Bouncy Castle 1.86 package layout, retained as deprecated aliases in 1.87. */
    @SuppressWarnings("deprecation")
    private static final class Bc186Access {
        private static void verifyAvailable() {
            lmsKeyGenParameterSpec("lms_sha256_n32_h5", "sha256_n32_w1");
            lmsHssKeyGenParameterSpec(
                    new String[] { "lms_sha256_n32_h5", "lms_sha256_n32_h5" },
                    new String[] { "sha256_n32_w2", "sha256_n32_w2" });
            xmssParameterSpec(2, "SHA256");
            xmssMtParameterSpec(20, 2, "SHA256");
            isLmsPrivateKey(null);
            isXmssPrivateKey(null);
            isXmssMtPrivateKey(null);
        }

        private static boolean isLmsPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey;
        }

        private static boolean isXmssPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey;
        }

        private static boolean isXmssMtPrivateKey(PrivateKey privateKey) {
            return privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey;
        }

        private static AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureName, String otsName) {
            return org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec.fromNames(
                    signatureName.replace('_', '-'), otsName.replace('_', '-'));
        }

        private static AlgorithmParameterSpec lmsHssKeyGenParameterSpec(String[] signatureNames, String[] otsNames) {
            if (signatureNames == null || otsNames == null || signatureNames.length == 0
                    || signatureNames.length != otsNames.length) {
                throw new IllegalArgumentException("HSS requires matching, non-empty LMS and LM-OTS parameter arrays");
            }
            org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec[] specs
                    = new org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec[signatureNames.length];
            for (int i = 0; i < specs.length; i++) {
                specs[i] = org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec.fromNames(
                        signatureNames[i].replace('_', '-'), otsNames[i].replace('_', '-'));
            }
            return new org.bouncycastle.pqc.jcajce.spec.LMSHSSKeyGenParameterSpec(specs);
        }

        private static AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest) {
            return new org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec(height, treeDigest);
        }

        private static AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest) {
            return new org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec(height, layers, treeDigest);
        }

        private static long getStatefulKeyRemaining(PrivateKey privateKey) {
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey xmss) {
                return xmss.getUsagesRemaining();
            }
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey xmssMt) {
                return xmssMt.getUsagesRemaining();
            }
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey lms) {
                return lms.getUsagesRemaining();
            }
            return -1;
        }

        private static long getStatefulKeyIndex(PrivateKey privateKey) {
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey xmss) {
                return xmss.getIndex();
            }
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey xmssMt) {
                return xmssMt.getIndex();
            }
            if (privateKey instanceof org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey lms) {
                return lms.getIndex();
            }
            return 0;
        }
    }
}
