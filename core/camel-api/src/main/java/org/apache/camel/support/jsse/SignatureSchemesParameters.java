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
package org.apache.camel.support.jsse;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list of TLS/SSL signature schemes for use in TLS handshakes. Signature schemes control which signature
 * algorithms are available during the TLS handshake, including post-quantum signature algorithms such as ML-DSA.
 */
public class SignatureSchemesParameters {
    private List<String> signatureScheme;

    /**
     * Returns a live reference to the list of signature scheme names.
     *
     * @return a reference to the list, never {@code null}
     */
    public List<String> getSignatureScheme() {
        if (this.signatureScheme == null) {
            this.signatureScheme = new ArrayList<>();
        }
        return this.signatureScheme;
    }

    public void addSignatureScheme(String scheme) {
        if (this.signatureScheme == null) {
            this.signatureScheme = new ArrayList<>();
        }
        this.signatureScheme.add(scheme.trim());
    }

    /**
     * Sets the signature schemes. It creates a copy of the given list.
     *
     * @param signatureScheme signature schemes
     */
    public void setSignatureScheme(List<String> signatureScheme) {
        this.signatureScheme = signatureScheme == null ? null : new ArrayList<>(signatureScheme);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SignatureSchemesParameters[signatureScheme=");
        builder.append(getSignatureScheme());
        builder.append("]");
        return builder.toString();
    }
}
