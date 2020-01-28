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
package org.apache.camel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.commands.internal.MatchUtil;
import org.apache.camel.commands.internal.RegexUtil;

/**
 * Abstract {@link org.apache.camel.commands.CamelController} that implementators should extend.
 */
public abstract class AbstractCamelController implements CamelController {

    @Override
    public List<Map<String, String>> getCamelContexts(String filter) throws Exception {
        List<Map<String, String>> answer = new ArrayList<>();

        List<Map<String, String>> context = getCamelContexts();
        if (filter != null) {
            filter = RegexUtil.wildcardAsRegex(filter);
        } else {
            filter = "*";
        }
        for (Map<String, String> entry : context) {
            String name = entry.get("name");
            if (name.equalsIgnoreCase(filter) || MatchUtil.matchWildcard(name, filter) || name.matches(filter)) {
                answer.add(entry);
            }
        }

        return answer;
    }

}
