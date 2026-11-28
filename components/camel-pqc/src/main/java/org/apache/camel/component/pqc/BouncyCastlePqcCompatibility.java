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

/** Typed facade over the adapter compiled against the installed Bouncy Castle release. */
public final class BouncyCastlePqcCompatibility {

    private BouncyCastlePqcCompatibility() {
    }

    public static AlgorithmParameterSpec namedParameterSpec(String algorithm, String parameterName) {
        return BouncyCastlePqcAdapterSupport.adapter().resolveParameterSpec(algorithm, parameterName);
    }

    public static AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureParameterName, String otsParameterName) {
        return BouncyCastlePqcAdapterSupport.adapter()
                .lmsKeyGenParameterSpec(signatureParameterName, otsParameterName);
    }

    public static AlgorithmParameterSpec lmsHssKeyGenParameterSpec(
            String[] signatureParameterNames, String[] otsParameterNames) {
        return BouncyCastlePqcAdapterSupport.adapter()
                .lmsHssKeyGenParameterSpec(signatureParameterNames, otsParameterNames);
    }

    public static AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest) {
        return BouncyCastlePqcAdapterSupport.adapter().xmssParameterSpec(height, treeDigest);
    }

    public static AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest) {
        return BouncyCastlePqcAdapterSupport.adapter().xmssMtParameterSpec(height, layers, treeDigest);
    }

    public static long getStatefulKeyRemaining(PrivateKey privateKey) {
        return BouncyCastlePqcAdapterSupport.adapter().getStatefulKeyRemaining(privateKey);
    }

    public static long getStatefulKeyIndex(PrivateKey privateKey) {
        return BouncyCastlePqcAdapterSupport.adapter().getStatefulKeyIndex(privateKey);
    }
}
