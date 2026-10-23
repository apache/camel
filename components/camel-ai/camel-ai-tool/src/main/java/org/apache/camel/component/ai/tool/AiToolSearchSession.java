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
package org.apache.camel.component.ai.tool;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Names discovered by tool search for a single request or conversation.
 * <p>
 * Only names are stored. Producers re-read the full definition from the latest filtered catalogue, so a changed or
 * removed tool cannot be replayed from a stale schema. This object is not stored in {@link AiToolRegistry}.
 *
 * @since 4.24
 */
public final class AiToolSearchSession {

    private final Set<String> discovered = new LinkedHashSet<>();

    /**
     * Remembers tools found by a search. Later names keep their first-seen order.
     */
    public void remember(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        synchronized (discovered) {
            discovered.addAll(names);
        }
    }

    /**
     * Names discovered so far, in the order they were first found.
     */
    public List<String> discoveredNames() {
        synchronized (discovered) {
            return List.copyOf(discovered);
        }
    }

    /**
     * Drops names that are no longer present in the current catalogue.
     */
    public void retainAvailable(Set<String> availableNames) {
        synchronized (discovered) {
            if (availableNames == null || availableNames.isEmpty()) {
                discovered.clear();
                return;
            }
            discovered.retainAll(availableNames);
        }
    }
}
