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
package org.apache.camel.language.semantic;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertyConfigurer;

/** Preserves adapter references so the language can validate their type and manage only instances it creates. */
public class SemanticLanguageConfigurer implements PropertyConfigurer {
    @Override
    public boolean configureRaw(CamelContext context, Object target, String name, Object value, boolean ignoreCase) {
        if (ignoreCase ? "adapter".equalsIgnoreCase(name) : "adapter".equals(name)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Semantic adapter selection must be a bean reference or class name");
            }
            ((SemanticLanguage) target).setAdapter((String) value);
            return true;
        }
        return false;
    }

    @Override
    public boolean configure(CamelContext context, Object target, String name, Object value, boolean ignoreCase) {
        return false;
    }
}
