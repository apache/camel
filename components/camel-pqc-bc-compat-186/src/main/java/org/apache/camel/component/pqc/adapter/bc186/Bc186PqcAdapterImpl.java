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
package org.apache.camel.component.pqc.adapter.bc186;

import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.component.pqc.spi.Bc186PqcAdapter;
import org.bouncycastle.jcajce.spec.FrodoKEMParameterSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jcajce.spec.SLHDSAParameterSpec;
import org.bouncycastle.pqc.jcajce.interfaces.LMSPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSMTPrivateKey;
import org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey;
import org.bouncycastle.pqc.jcajce.spec.BIKEParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.HQCParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.LMSHSSKeyGenParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.LMSKeyGenParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.MayoParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRULPRimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.NTRUParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SABERParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SNTRUPrimeParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SnovaParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.XMSSMTParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec;

/** Direct, non-reflective integration with the public Bouncy Castle 1.86 JCA API. */
public final class Bc186PqcAdapterImpl implements Bc186PqcAdapter {

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

    @Override
    public String bcVersion() {
        return "1.86";
    }

    @Override
    public AlgorithmParameterSpec resolveParameterSpec(String algorithm, String parameterName) {
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

    @Override
    public AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureParameterName, String otsParameterName) {
        return LMSKeyGenParameterSpec.fromNames(
                signatureParameterName.replace('_', '-'), otsParameterName.replace('_', '-'));
    }

    @Override
    public AlgorithmParameterSpec lmsHssKeyGenParameterSpec(
            String[] signatureParameterNames, String[] otsParameterNames) {
        if (signatureParameterNames == null || otsParameterNames == null
                || signatureParameterNames.length == 0
                || signatureParameterNames.length != otsParameterNames.length) {
            throw new IllegalArgumentException("HSS requires matching, non-empty LMS and LM-OTS parameter arrays");
        }
        LMSKeyGenParameterSpec[] specs = new LMSKeyGenParameterSpec[signatureParameterNames.length];
        for (int i = 0; i < specs.length; i++) {
            specs[i] = LMSKeyGenParameterSpec.fromNames(
                    signatureParameterNames[i].replace('_', '-'), otsParameterNames[i].replace('_', '-'));
        }
        return new LMSHSSKeyGenParameterSpec(specs);
    }

    @Override
    public AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest) {
        return new XMSSParameterSpec(height, treeDigest);
    }

    @Override
    public AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest) {
        return new XMSSMTParameterSpec(height, layers, treeDigest);
    }

    @Override
    public long getStatefulKeyRemaining(PrivateKey privateKey) {
        if (privateKey instanceof XMSSPrivateKey xmss) {
            return xmss.getUsagesRemaining();
        }
        if (privateKey instanceof XMSSMTPrivateKey xmssMt) {
            return xmssMt.getUsagesRemaining();
        }
        if (privateKey instanceof LMSPrivateKey lms) {
            return lms.getUsagesRemaining();
        }
        return -1;
    }

    @Override
    public long getStatefulKeyIndex(PrivateKey privateKey) {
        if (privateKey instanceof XMSSPrivateKey xmss) {
            return xmss.getIndex();
        }
        if (privateKey instanceof XMSSMTPrivateKey xmssMt) {
            return xmssMt.getIndex();
        }
        if (privateKey instanceof LMSPrivateKey lms) {
            return lms.getIndex();
        }
        return 0;
    }
}
