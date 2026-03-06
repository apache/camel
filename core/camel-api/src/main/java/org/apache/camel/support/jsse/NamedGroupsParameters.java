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
 * Represents a list of TLS/SSL named groups (also known as elliptic curves or key exchange groups) for use in TLS
 * handshakes. Named groups control which key exchange algorithms are available during the TLS handshake, including
 * post-quantum hybrid groups such as {@code X25519MLKEM768}.
 */
public class NamedGroupsParameters {
    private List<String> namedGroup;

    /**
     * Returns a live reference to the list of named group names.
     *
     * @return a reference to the list, never {@code null}
     */
    public List<String> getNamedGroup() {
        if (this.namedGroup == null) {
            this.namedGroup = new ArrayList<>();
        }
        return this.namedGroup;
    }

    public void addNamedGroup(String group) {
        if (this.namedGroup == null) {
            this.namedGroup = new ArrayList<>();
        }
        this.namedGroup.add(group.trim());
    }

    /**
     * Sets the named groups. It creates a copy of the given list.
     *
     * @param namedGroup named groups
     */
    public void setNamedGroup(List<String> namedGroup) {
        this.namedGroup = namedGroup == null ? null : new ArrayList<>(namedGroup);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("NamedGroupsParameters[namedGroup=");
        builder.append(getNamedGroup());
        builder.append("]");
        return builder.toString();
    }
}
