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
package org.apache.camel.support;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.util.StringHelper;

/**
 * {@link VariableRepository} which is local per {@link Exchange} to hold request-scoped variables for message headers.
 */
final class HeaderVariableRepository extends AbstractVariableRepository {

    public HeaderVariableRepository(CamelContext camelContext) {
        setCamelContext(camelContext);
        // ensure its started
        ServiceHelper.startService(this);
    }

    @Override
    public String getId() {
        return "header";
    }

    @Override
    public Object getVariable(String name) {
        Object answer = super.getVariable(name);
        if (answer == null && !name.contains(".")) {
            String prefix = name + ".";
            // we want all headers for a given variable
            Map<String, Object> map = new CaseInsensitiveMap();
            for (Map.Entry<String, Object> entry : getVariables().entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    key = StringHelper.after(key, prefix);
                    map.put(key, entry.getValue());
                }
            }
            return map;
        }
        if (answer instanceof StreamCache sc) {
            // reset so the cache is ready to be used as a variable
            sc.reset();
        }
        return answer;
    }

}
