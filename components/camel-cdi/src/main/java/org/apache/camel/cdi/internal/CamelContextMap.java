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
package org.apache.camel.cdi.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

/**
 * A helper class to be able to resolve CamelContext instances by their name
 */
@ApplicationScoped
public class CamelContextMap {
    @Inject
    private Instance<CamelContext> camelContexts;

    private Map<String, CamelContext> camelContextMap = new HashMap<String, CamelContext>();

    @PostConstruct
    public void start() {
        ObjectHelper.notNull(camelContexts, "camelContexts");

        Iterator<CamelContext> iterator = camelContexts.iterator();
        while (iterator.hasNext()) {
            CamelContext camelContext = iterator.next();
            camelContextMap.put(camelContext.getName(), camelContext);
        }
    }

    /**
     * Returns the {@link CamelContext} for the given context name
     */
    public CamelContext getCamelContext(String name) {
        CamelContext answer = camelContextMap.get(name);
        if (answer == null && ObjectHelper.isEmpty(name)) {
            // lets return the first one for the default context?
            Collection<CamelContext> values = camelContextMap.values();
            for (CamelContext value : values) {
                answer = value;
                break;
            }
        }
        return answer;

    }

    /**
     * Returns the CamelContext for the given name or throw an exception
     */
    public CamelContext getMandatoryCamelContext(String contextName) {
        CamelContext camelContext = getCamelContext(contextName);
        ObjectHelper.notNull(camelContext, "No CamelContext found for name '" + contextName + "' when available names are " + camelContextMap.keySet());
        return camelContext;
    }

    public Map<String, CamelContext> getCamelContextMap() {
        return camelContextMap;
    }
}
