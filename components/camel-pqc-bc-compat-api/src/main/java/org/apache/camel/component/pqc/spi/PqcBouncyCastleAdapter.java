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
package org.apache.camel.component.pqc.spi;

import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;

/** JDK-only contract implemented by the version-specific Bouncy Castle adapters. */
public interface PqcBouncyCastleAdapter {

    /** Bouncy Castle API level against which this adapter was compiled. */
    String bcVersion();

    /** Resolve a named parameter set without exposing Bouncy Castle types to camel-pqc. */
    AlgorithmParameterSpec resolveParameterSpec(String algorithm, String parameterName);

    AlgorithmParameterSpec lmsKeyGenParameterSpec(String signatureParameterName, String otsParameterName);

    AlgorithmParameterSpec lmsHssKeyGenParameterSpec(
            String[] signatureParameterNames, String[] otsParameterNames);

    AlgorithmParameterSpec xmssParameterSpec(int height, String treeDigest);

    AlgorithmParameterSpec xmssMtParameterSpec(int height, int layers, String treeDigest);

    /** Returns -1 for a key which is not an LMS/HSS/XMSS/XMSSMT stateful private key. */
    long getStatefulKeyRemaining(PrivateKey privateKey);

    /** Returns 0 for a key which is not an LMS/HSS/XMSS/XMSSMT stateful private key. */
    long getStatefulKeyIndex(PrivateKey privateKey);
}
