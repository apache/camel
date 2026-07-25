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
package org.apache.camel.dsl.jbang.core.commands.tui;

/**
 * Parses {@code camel.tui.*} history limit settings. A limit of {@code 0} disables recall and persistence; when unset,
 * {@link #DEFAULT_LIMIT} applies.
 */
final class TuiHistoryLimits {

    static final int DEFAULT_LIMIT = 100;

    private TuiHistoryLimits() {
    }

    static int resolveLimit(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(0, value);
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }
}
