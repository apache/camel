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
public class SecureSocketProtocolsParameters {
    private List<String> secureSocketProtocol;

    /**
     * Returns a live reference to the list of secure socket protocol names.
     *
     * @return a reference to the list, never {@code null}
     */
    public List<String> getSecureSocketProtocol() {
        if (this.secureSocketProtocol == null) {
            this.secureSocketProtocol = new ArrayList<>();
        }
        return this.secureSocketProtocol;
    }

    /**
     * Sets the list of secure socket protocol names. It creates a copy of the given protocol list.
     *
     * @param secureSocketProtocol list of secure socket protocol names
     */
    public void setSecureSocketProtocol(List<String> secureSocketProtocol) {
        this.secureSocketProtocol = secureSocketProtocol == null ? null : new ArrayList<>(secureSocketProtocol);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SecureSocketProtocolsParameters[secureSocketProtocol=");
        builder.append(getSecureSocketProtocol());
        builder.append("]");
        return builder.toString();
    }
}
