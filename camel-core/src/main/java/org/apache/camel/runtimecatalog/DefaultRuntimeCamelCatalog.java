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
package org.apache.camel.runtimecatalog;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;

/**
 * Default {@link RuntimeCamelCatalog}.
 */
public class DefaultRuntimeCamelCatalog extends AbstractCamelCatalog implements RuntimeCamelCatalog {

    // cache of operation -> result
    private final Map<String, Object> cache = new HashMap<String, Object>();
    private boolean caching;

    /**
     * Creates the {@link RuntimeCamelCatalog} without caching enabled.
     *
     * @param camelContext  the camel context
     */
    public DefaultRuntimeCamelCatalog(CamelContext camelContext) {
        this(camelContext, false);
    }

    /**
     * Creates the {@link RuntimeCamelCatalog}
     *
     * @param camelContext  the camel context
     * @param caching  whether to use cache
     */
    public DefaultRuntimeCamelCatalog(CamelContext camelContext, boolean caching) {
        this.caching = caching;
        setJSonSchemaResolver(new CamelContextJSonSchemaResolver(camelContext));
    }

    @Override
    public void start() throws Exception {
        // noop
    }

    @Override
    public void stop() throws Exception {
        cache.clear();
    }

    @Override
    public String modelJSonSchema(String name) {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("model-" + name);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getModelJSonSchema(name);
            if (caching) {
                cache.put("model-" + name, answer);
            }
        }

        return answer;
    }

    @Override
    public String componentJSonSchema(String name) {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("component-" + name);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getComponentJSonSchema(name);
            if (caching) {
                cache.put("component-" + name, answer);
            }
        }

        return answer;
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        String answer = null;
        if (caching) {
            answer = (String) cache.get("dataformat-" + name);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getDataFormatJSonSchema(name);
            if (caching) {
                cache.put("dataformat-" + name, answer);
            }
        }

        return answer;
    }

    @Override
    public String languageJSonSchema(String name) {
        // if we try to look method then its in the bean.json file
        if ("method".equals(name)) {
            name = "bean";
        }

        String answer = null;
        if (caching) {
            answer = (String) cache.get("language-" + name);
        }

        if (answer == null) {
            answer = getJSonSchemaResolver().getLanguageJSonSchema(name);
            if (caching) {
                cache.put("language-" + name, answer);
            }
        }

        return answer;
    }

}
