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

/** Read-only browsing, independent of the destination's append capability. */
public interface SemanticAuditReader {
    SemanticAuditPage query(SemanticAuditQuery query) throws Exception;

    Optional<SemanticAuditRecord> get(String eventId) throws Exception;

    /**
     * Resolve up to 100 linked event IDs. Missing records are absent; remote backends should override with one query.
     */
    default Map<String, SemanticAuditRecord> getAll(List<String> eventIds) throws Exception {
        if (eventIds.size() > 100) {
            throw new IllegalArgumentException("Too many audit evidence references");
        }
        Map<String, SemanticAuditRecord> records = new LinkedHashMap<>();
        for (String id : eventIds) {
            if (!records.containsKey(id)) {
                get(id).ifPresent(record -> records.put(id, record));
            }
        }
        return Map.copyOf(records);
    }
}
