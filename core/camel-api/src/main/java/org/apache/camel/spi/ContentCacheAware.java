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
package org.apache.camel.spi;

/**
 * Represents a resource-based component (or endpoint) whose loaded resource content can be cached. Implementations
 * expose the tri-state {@code contentCache} setting: {@code null} indicates the user has not explicitly configured the
 * option (and the implementation will resolve to its own default), while {@code true} or {@code false} are explicit
 * configurations.
 *
 * The tri-state form allows runtime infrastructure (e.g. Camel Main) to safely flip the default in dev/reload scenarios
 * without overriding user-supplied values.
 */
public interface ContentCacheAware {

    /**
     * The raw content-cache setting.
     *
     * @return {@code null} if not explicitly configured, otherwise the configured value.
     */
    Boolean getContentCache();

    /**
     * Sets the content-cache option. Pass {@code null} to mark the option as unset (so callers using
     * {@link #getContentCache()} can distinguish "not configured" from an explicit {@code true}/{@code false}).
     */
    void setContentCache(Boolean contentCache);
}
