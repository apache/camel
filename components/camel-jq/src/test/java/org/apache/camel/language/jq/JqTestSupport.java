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
package org.apache.camel.language.jq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.test.junit5.CamelTestSupport;

public abstract class JqTestSupport extends CamelTestSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode node(String key, String value, String... keyVals) {
        ObjectNode answer = MAPPER.createObjectNode();

        answer.put(key, value);

        for (int i = 0; i < keyVals.length; i += 2) {
            answer.put(
                    keyVals[i],
                    keyVals[i + 1]);
        }

        return answer;
    }
}
