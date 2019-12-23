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
package org.apache.camel.component.salesforce.api.dto.approval;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds approvals resource data.
 *
 * @see <a href=
 *      "https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_process_approvals.htm">
 *      https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/dome_process_approvals.htm</a>
 */
public final class Approvals implements Serializable {

    /**
     * Information about approval tied to specific Salesforce object.
     */
    public static final class Info implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String description;

        private final String id;

        private final String name;

        private final String object;

        private final int sortOrder;

        @JsonCreator
        Info(@JsonProperty("id")
        final String id, @JsonProperty("description")
        final String description, @JsonProperty("name")
        final String name, @JsonProperty("object")
        final String object, @JsonProperty("sortOrder")
        final int sortOrder) {
            this.description = description;
            this.id = id;
            this.name = name;
            this.object = object;
            this.sortOrder = sortOrder;
        }

        public String getDescription() {
            return description;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getObject() {
            return object;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        @Override
        public String toString() {
            final StringBuilder buildy = new StringBuilder();

            buildy.append("Id: ").append(id);

            buildy.append(", Name: ").append(name);

            buildy.append(", Object: ").append(object);

            buildy.append(", Description: ").append(description);

            buildy.append(", SortOrder: ").append(sortOrder);

            return buildy.toString();
        }
    }

    private static final long serialVersionUID = 1L;

    private final Map<String, List<Info>> approvals;

    @JsonCreator
    Approvals(@JsonProperty("approvals")
    final Map<String, List<Info>> approvals) {
        this.approvals = Optional.ofNullable(approvals).orElse(Collections.emptyMap());
    }

    /**
     * Returns approvals for specific Salesforce object type.
     *
     * @param object type
     * @return approvals of specified type
     */
    public List<Info> approvalsFor(final String object) {
        return approvals.getOrDefault(object, Collections.emptyList());
    }

    /**
     * Returns approvals by Salesforce object type. You might have approvals for
     * "Account" and "Case" Salesforce objects, then the resulting map would
     * hold a list of {@link Info} objects keyed by the object type, i.e.:
     *
     * <pre>
     * Approvals approvals = ...;
     * List<Info> accountApprovals = approvals.getApprovals("Account");
     * List<Info> caseApprovals = approvals.getApprovals("Case");
     * </pre>
     *
     * @return approval info by object type
     */
    public Map<String, List<Info>> getApprovals() {
        return approvals;
    }

    @Override
    public String toString() {
        return approvals.toString();
    }
}
