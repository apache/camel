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
package org.apache.camel.runtimecatalog.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.tooling.model.OtherModel;

/**
 * Default {@link RuntimeCamelCatalog}.
 */
public class DefaultRuntimeCamelCatalog extends AbstractCamelCatalog implements RuntimeCamelCatalog {

    // cache of operation -> result
    private final Map<String, Object> cache = new HashMap<>();
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
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        cache.clear();
    }

    @Override
    public String modelJSonSchema(String name) {
        return cache("eip-" + name, name, super::modelJSonSchema);
    }

    @Override
    public EipModel eipModel(String name) {
        return cache("eip-model-" + name, name, super::eipModel);
    }

    @Override
    public String componentJSonSchema(String name) {
        return cache("component-" + name, name, super::componentJSonSchema);
    }

    @Override
    public ComponentModel componentModel(String name) {
        return cache("component-model-" + name, name, super::componentModel);
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        return cache("dataformat-" + name, name, super::dataFormatJSonSchema);
    }

    @Override
    public DataFormatModel dataFormatModel(String name) {
        return cache("dataformat-model-" + name, name, super::dataFormatModel);
    }

    @Override
    public String languageJSonSchema(String name) {
        return cache("language-" + name, name, super::languageJSonSchema);
    }

    @Override
    public LanguageModel languageModel(String name) {
        return cache("language-model-" + name, name, super::languageModel);
    }

    @Override
    public String otherJSonSchema(String name) {
        return cache("other-" + name, name, super::otherJSonSchema);
    }

    @Override
    public OtherModel otherModel(String name) {
        return cache("other-model-" + name, name, super::otherModel);
    }

    public String mainJSonSchema() {
        return cache("main", "main", k -> super.mainJSonSchema());
    }

    public MainModel mainModel() {
        return cache("main-model", "main-model", k -> super.mainModel());
    }

    @SuppressWarnings("unchecked")
    private <T> T cache(String key, String name, Function<String, T> loader) {
        if (caching) {
            T t = (T) cache.get(key);
            if (t == null) {
                t = loader.apply(name);
                if (t != null) {
                    cache.put(key, t);
                }
            }
            return t;
        } else {
            return loader.apply(name);
        }
    }

}
