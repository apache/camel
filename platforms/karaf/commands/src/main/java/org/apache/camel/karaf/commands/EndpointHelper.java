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
package org.apache.camel.karaf.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EndpointHelper {

    private static final Pattern PATTERN = Pattern.compile("\"(.+?)\"");

    private EndpointHelper() {
    }

    public static List<String[]> parseEndpointExplainJson(String json) {
        // parse line by line
        List<String[]> answer = new ArrayList<>();

        // skip first 2 lines as they are leading
        String[] lines = json.split("\n");
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];

            Matcher matcher = PATTERN.matcher(line);
            String option = null;
            String value = null;
            String description = null;
            int count = 0;
            while (matcher.find()) {
                count++;
                if (count == 1) {
                    option = matcher.group(1);
                } else if (count == 3) {
                    value = matcher.group(1);
                } else if (count == 5) {
                    description = matcher.group(1);
                }
            }

            if (option != null) {
                String[] row = new String[]{option, value, description};
                answer.add(row);
            }
        }

        return answer;
    }
}
