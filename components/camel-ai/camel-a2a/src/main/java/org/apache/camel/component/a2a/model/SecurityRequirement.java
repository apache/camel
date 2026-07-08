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
package org.apache.camel.component.a2a.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A2A v1.0 security requirement. Each entry in {@code schemes} must be satisfied together; multiple
 * {@link SecurityRequirement} objects are alternatives.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SecurityRequirement {
    private Map<String, StringList> schemes = new LinkedHashMap<>();

    public SecurityRequirement() {
    }

    public static SecurityRequirement of(String schemeName, List<String> scopes) {
        SecurityRequirement requirement = new SecurityRequirement();
        requirement.schemes.put(schemeName, new StringList(scopes));
        return requirement;
    }

    public static SecurityRequirement fromScopeMap(Map<String, List<String>> schemes) {
        SecurityRequirement requirement = new SecurityRequirement();
        requirement.setScopeMap(schemes);
        return requirement;
    }

    @JsonProperty("schemes")
    public Map<String, StringList> getSchemes() {
        return Collections.unmodifiableMap(schemes);
    }

    @JsonProperty("schemes")
    public void setSchemes(Map<String, Object> schemes) {
        this.schemes = new LinkedHashMap<>();
        if (schemes == null) {
            return;
        }
        schemes.forEach((name, scopes) -> this.schemes.put(name, StringList.from(scopes)));
    }

    public void setScopeMap(Map<String, List<String>> schemes) {
        this.schemes = new LinkedHashMap<>();
        if (schemes == null) {
            return;
        }
        schemes.forEach((name, scopes) -> this.schemes.put(name, new StringList(scopes)));
    }

    @JsonIgnore
    public Map<String, List<String>> asScopeMap() {
        Map<String, List<String>> answer = new LinkedHashMap<>();
        schemes.forEach((name, scopes) -> answer.put(name, scopes.getList()));
        return Collections.unmodifiableMap(answer);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return schemes.isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class StringList {
        private List<String> list = new ArrayList<>();

        public StringList() {
        }

        public StringList(List<String> list) {
            setList(list);
        }

        public List<String> getList() {
            return Collections.unmodifiableList(list);
        }

        public void setList(List<String> list) {
            this.list = list != null ? new ArrayList<>(list) : new ArrayList<>();
        }

        static StringList from(Object value) {
            if (value instanceof StringList stringList) {
                return new StringList(stringList.getList());
            }
            if (value instanceof List<?> list) {
                return new StringList(toStringList(list));
            }
            if (value instanceof Map<?, ?> map) {
                Object list = map.get("list");
                if (list instanceof List<?> values) {
                    return new StringList(toStringList(values));
                }
            }
            return new StringList();
        }

        private static List<String> toStringList(List<?> values) {
            List<String> answer = new ArrayList<>(values.size());
            for (Object value : values) {
                answer.add(String.valueOf(value));
            }
            return answer;
        }
    }
}
