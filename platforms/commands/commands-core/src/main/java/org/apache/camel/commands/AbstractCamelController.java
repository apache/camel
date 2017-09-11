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
package org.apache.camel.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.JsonSchemaHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.commands.internal.MatchUtil.matchWildcard;
import static org.apache.camel.commands.internal.RegexUtil.wildcardAsRegex;

/**
 * Abstract {@link org.apache.camel.commands.CamelController} that implementators should extend.
 */
public abstract class AbstractCamelController implements CamelController {

    @Override
    public List<Map<String, String>> getCamelContexts(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();

        List<Map<String, String>> context = getCamelContexts();
        if (filter != null) {
            filter = wildcardAsRegex(filter);
        } else {
            filter = "*";
        }
        for (Map<String, String> entry : context) {
            String name = entry.get("name");
            if (name.equalsIgnoreCase(filter) || matchWildcard(name, filter) || name.matches(filter)) {
                answer.add(entry);
            }
        }

        return answer;
    }

    protected Map<String, Object> loadProperties(String json, String group, Map<String, Object> answer) {
        List<Map<String, String>> kv = JsonSchemaHelper.parseJsonSchema(group, json, true);
        if (kv.isEmpty()) {
            return answer;
        }

        Map<String, Object> groupkv = new LinkedHashMap<>();
        answer.put(group, groupkv);

        for (Map<String, String> map : kv) {
            boolean first = true;
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (first) {
                    if (!ObjectHelper.equal(entry.getKey(), "name")) {
                        throw new IllegalStateException("First entry should be the property name");
                    }
                    groupkv.put(entry.getValue(), properties);
                    first = false;
                } else {
                    properties.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return answer;
    }

}
