/**
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
package org.apache.camel.util.jsse;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list of TLS/SSL cipher suite names.
 */
public class CipherSuitesParameters {
    private List<String> cipherSuite;

    /**
     * Returns a live reference to the list of cipher suite names.
     *
     * @return a reference to the list, never {@code null}
     */
    public List<String> getCipherSuite() {
        if (this.cipherSuite == null) {
            this.cipherSuite = new ArrayList<>();
        }
        return this.cipherSuite;
    }

    /**
     * Sets the cipher suite. It creates a copy of the given cipher suite.
     *
     * @param cipherSuite cipher suite
     */
    public void setCipherSuite(List<String> cipherSuite) {
        this.cipherSuite = cipherSuite == null ? null : new ArrayList<>(cipherSuite);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CipherSuitesParameters[cipherSuite=");
        builder.append(getCipherSuite());
        builder.append("]");
        return builder.toString();
    }
}
