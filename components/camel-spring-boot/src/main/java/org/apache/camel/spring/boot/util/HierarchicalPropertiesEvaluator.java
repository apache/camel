/**
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
package org.apache.camel.spring.boot.util;

import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.core.env.Environment;

public final class HierarchicalPropertiesEvaluator {
    private HierarchicalPropertiesEvaluator() {
    }

    public static boolean evaluate(Environment environment, String... prefixes) {
        boolean answer = true;

        for (String prefix : prefixes) {
            answer = isEnabled(environment, prefix, answer);
        }

        return answer;
    }

    private static boolean isEnabled(Environment environment, String prefix, boolean defaultValue) {
        RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(
            environment,
            prefix.endsWith(".") ? prefix : prefix + "."
        );

        return resolver.getProperty("enabled", Boolean.class, defaultValue);
    }
}
