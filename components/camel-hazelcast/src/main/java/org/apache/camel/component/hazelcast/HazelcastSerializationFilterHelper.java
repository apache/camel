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
package org.apache.camel.component.hazelcast;

import com.hazelcast.config.ClassFilter;
import com.hazelcast.config.Config;
import com.hazelcast.config.JavaSerializationFilterConfig;
import com.hazelcast.config.SerializationConfig;

/**
 * Applies a default {@link JavaSerializationFilterConfig} to Hazelcast {@link Config} instances built by Camel when the
 * user has not configured one. The default whitelists {@code java.}, {@code javax.} and {@code org.apache.camel.} class
 * name prefixes and blacklists {@code java.net.}.
 * <p>
 * If the supplied {@link Config} already declares a {@link JavaSerializationFilterConfig} (e.g. provided by the user
 * via a reference or XML/YAML configuration), it is left untouched.
 */
public final class HazelcastSerializationFilterHelper {

    static final String[] DEFAULT_WHITELIST_PREFIXES = { "java.", "javax.", "org.apache.camel." };
    static final String[] DEFAULT_BLACKLIST_PREFIXES = { "java.net." };

    private HazelcastSerializationFilterHelper() {
    }

    /**
     * Applies the default {@link JavaSerializationFilterConfig} on the {@link SerializationConfig} of the given
     * {@link Config} when one is not already set. Has no effect when {@code config} is {@code null} or the user has
     * already configured a {@link JavaSerializationFilterConfig}.
     */
    public static void applyDefault(Config config) {
        if (config == null) {
            return;
        }
        SerializationConfig serializationConfig = config.getSerializationConfig();
        if (serializationConfig == null) {
            return;
        }
        if (serializationConfig.getJavaSerializationFilterConfig() != null) {
            return;
        }
        JavaSerializationFilterConfig filter = new JavaSerializationFilterConfig();
        filter.setWhitelist(new ClassFilter().addPrefixes(DEFAULT_WHITELIST_PREFIXES));
        filter.setBlacklist(new ClassFilter().addPrefixes(DEFAULT_BLACKLIST_PREFIXES));
        serializationConfig.setJavaSerializationFilterConfig(filter);
    }
}
