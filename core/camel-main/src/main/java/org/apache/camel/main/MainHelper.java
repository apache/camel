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
package org.apache.camel.main;

import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import org.apache.camel.util.OrderedProperties;

public final class MainHelper {
    private MainHelper() {
    }

    public static String toEnvVar(String name) {
        return name.toUpperCase(Locale.US).replaceAll("[^\\w]", "-").replace('-', '_');
    }

    public static Optional<String> lookupPropertyFromSysOrEnv(String name) {
        String answer = System.getProperty(name);
        if (answer == null) {
            answer = System.getenv(toEnvVar(name));
        }

        return Optional.ofNullable(answer);
    }

    public static Properties loadEnvironmentVariablesAsProperties(String[] prefixes) {
        Properties answer = new OrderedProperties();
        if (prefixes == null || prefixes.length == 0) {
            return answer;
        }

        for (String prefix : prefixes) {
            final String pk = prefix.toUpperCase(Locale.US).replaceAll("[^\\w]", "-");
            final String pk2 = pk.replace('-', '_');
            System.getenv().forEach((k, v) -> {
                k = k.toUpperCase(Locale.US);
                if (k.startsWith(pk) || k.startsWith(pk2)) {
                    String key = k.toLowerCase(Locale.ENGLISH).replace('_', '.');
                    answer.put(key, v);
                }
            });
        }

        return answer;
    }

}
