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
package org.apache.camel.tracing;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * An adapter to extract tracing attributes from a tracing span.
 */
public interface ExtractAdapter {

    /**
     * Extract an iterator of attributes from the current tracing span.
     */
    Iterator<Map.Entry<String, Object>> iterator();

    /**
     * Extract an attribute from the current tracing span.
     *
     * @param key the attribute key
     */
    Object get(String key);

    /**
     * Get the attribute keys for the current tracing span.
     */
    Set<String> keys();
}
