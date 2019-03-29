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
package org.apache.camel.impl.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.support.service.ServiceSupport;

public abstract class AbstractServiceRegistry extends ServiceSupport implements ServiceRegistry {
    private final Map<String, Object> attributes;
    private int order;
    private String id;
    private CamelContext camelContext;

    protected AbstractServiceRegistry() {
        this(null, null);
    }

    protected AbstractServiceRegistry(String id) {
        this(id, null);
    }

    protected AbstractServiceRegistry(String id, CamelContext camelContext) {
        this.order = Ordered.LOWEST;
        this.id = id;
        this.camelContext = camelContext;
        this.attributes = new HashMap<>();
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes.clear();
        this.attributes.putAll(attributes);
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
