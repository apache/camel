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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.RestError;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

@XStreamAlias("results")
public final class ReferenceId implements Serializable {
    private static final long serialVersionUID = 1L;

    @XStreamImplicit
    private final List<RestError> errors;

    private final String id;

    private final String referenceId;

    @JsonCreator
    ReferenceId(@JsonProperty("referenceId")
    final String referenceId, @JsonProperty("id")
    final String id, @JsonProperty("errors")
    final List<RestError> errors) {
        this.referenceId = referenceId;
        this.id = id;
        this.errors = errors;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ReferenceId)) {
            return false;
        }

        final ReferenceId other = (ReferenceId)obj;

        return Objects.equals(id, other.id) && Objects.equals(referenceId, other.referenceId) && Objects.equals(getErrors(), other.getErrors());
    }

    public List<RestError> getErrors() {
        return unmodifiableList(ofNullable(errors).orElse(emptyList()));
    }

    public String getId() {
        return id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (errors == null ? 0 : errors.hashCode());
        result = prime * result + (id == null ? 0 : id.hashCode());
        result = prime * result + (referenceId == null ? 0 : referenceId.hashCode());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder buildy = new StringBuilder("Reference: ").append(referenceId).append(", Id: ");

        final List<RestError> anyErrors = getErrors();
        if (anyErrors.isEmpty()) {
            buildy.append(", with no errors");
        } else {
            buildy.append(", with ");
            buildy.append(anyErrors.size());
            buildy.append(" error(s)");
        }

        return buildy.toString();
    }
}
