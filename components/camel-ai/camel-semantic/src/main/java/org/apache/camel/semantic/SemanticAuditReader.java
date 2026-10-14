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
package org.apache.camel.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only browsing, independent of the destination's append capability. */
public interface SemanticAuditReader extends SemanticAuditCapabilities {
    /** Exact filters implemented by this reader. New relationships are optional for existing backends. */
    default Set<String> getSupportedFilters() {
        return Set.of("category", "expert", "routeId", "breadcrumbId");
    }

    SemanticAuditPage query(SemanticAuditQuery query) throws Exception;

    Optional<SemanticAuditRecord> get(String eventId) throws Exception;

    /**
     * Retrieve records by ID. Missing records are absent; retrieval failures must propagate to the caller. Callers
     * bound requests and responses. Backends may split retrieval into storage-specific batches, but must not silently
     * truncate requested IDs. This contract has no evidence-specific limit.
     */
    default Map<String, SemanticAuditRecord> getAll(List<String> eventIds) throws Exception {
        Map<String, SemanticAuditRecord> records = new LinkedHashMap<>();
        for (String id : eventIds.stream().distinct().toList()) {
            get(id).ifPresent(record -> records.put(id, record));
        }
        return Map.copyOf(records);
    }
}
