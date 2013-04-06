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
package org.apache.camel.builder.script;

import org.apache.camel.CamelContext;

public class ScriptPropertiesFunction implements PropertiesFunction {

    private final CamelContext context;

    public ScriptPropertiesFunction(CamelContext context) {
        this.context = context;
    }

    @Override
    public String resolve(String key) throws Exception {
        if (key == null) {
            return null;
        }

        String token = context.getPropertyPrefixToken();
        if (token == null) {
            return key;
        }

        if (!key.contains(token)) {
            // enclose key with tokens so placeholder can lookup and resolve it
            key = context.getPropertyPrefixToken() + key + context.getPropertySuffixToken();
        }
        return context.resolvePropertyPlaceholders(key);
    }

}
