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
package org.apache.camel.component.milo;

import java.io.Serializable;

import static java.util.Objects.requireNonNull;

import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class NamespaceId {
    private final String uri;
    private final UShort numeric;

    public NamespaceId(final String uri) {
        requireNonNull(uri);

        this.uri = uri;
        this.numeric = null;
    }

    public NamespaceId(final UShort numeric) {
        requireNonNull(numeric);

        this.uri = null;
        this.numeric = numeric;
    }

    public String getUri() {
        return this.uri;
    }

    public UShort getNumeric() {
        return this.numeric;
    }

    public boolean isNumeric() {
        return this.numeric != null;
    }

    @Override
    public String toString() {
        if (isNumeric()) {
            return String.format("[Namespace - numeric: %s]", this.numeric);
        } else {
            return String.format("[Namespace - URI: %s]", this.uri);
        }
    }

    public Serializable getValue() {
        return this.uri != null ? this.uri : this.numeric;
    }

    public static NamespaceId fromExpandedNodeId(final ExpandedNodeId id) {
        if (id == null) {
            return null;
        }

        if (id.getNamespaceUri() != null) {
            return new NamespaceId(id.getNamespaceUri());
        }
        if (id.getNamespaceIndex() != null) {
            return new NamespaceId(id.getNamespaceIndex());
        }

        throw new IllegalStateException(String.format("Unknown namespace type"));
    }
}
