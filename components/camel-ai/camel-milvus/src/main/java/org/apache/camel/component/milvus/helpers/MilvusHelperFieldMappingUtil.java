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
package org.apache.camel.component.milvus.helpers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared utility for parsing {@code textFieldMappings} strings used by {@link MilvusHelperInsert} and
 * {@link MilvusHelperUpsert}.
 */
final class MilvusHelperFieldMappingUtil {

    private MilvusHelperFieldMappingUtil() {
    }

    /**
     * Parses a comma-separated mapping string into an ordered map of field name to variable name.
     *
     * @param  textFieldMappings        the mappings string (e.g., {@code field1=var1,field2=var2})
     * @return                          an ordered map from field name to variable name
     * @throws IllegalArgumentException if a mapping entry does not contain exactly one {@code =} separator
     */
    static Map<String, String> parseMappings(String textFieldMappings) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String mapping : textFieldMappings.split(",")) {
            String[] pair = mapping.trim().split("=");
            if (pair.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid textFieldMappings entry: '" + mapping.trim()
                                                   + "'. Expected format: fieldName=variableName");
            }
            result.put(pair[0].trim(), pair[1].trim());
        }
        return result;
    }
}
