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

import org.apache.camel.CamelContext;

public class DummyCamelController extends AbstractLocalCamelController {

    private CamelContext camelContext;

    public DummyCamelController(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public List<CamelContext> getLocalCamelContexts() {
        List<CamelContext> answer = new ArrayList<CamelContext>(1);
        answer.add(camelContext);
        return answer;
    }

    @Override
    public List<Map<String, String>> getCamelContexts() throws Exception {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>(1);
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("name", camelContext.getName());
        row.put("state", camelContext.getStatus().name());
        row.put("uptime", camelContext.getUptime());
        answer.add(row);
        return answer;
    }
}
