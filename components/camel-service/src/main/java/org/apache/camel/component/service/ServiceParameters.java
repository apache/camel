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
package org.apache.camel.component.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.util.ObjectHelper;

public class ServiceParameters {
    private String id;
    private String name;
    private String host;
    private int port;
    private Map<String, String> meta;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setPort(String port) {
        setPort(Integer.parseInt(port));
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }

    public void addMeta(String key, String value) {
        if (this.meta == null) {
            this.meta = new HashMap<>();
        }

        this.meta.put(key, value);
    }

    public void addAllMeta(Map<String, String> meta) {
        if (this.meta == null) {
            this.meta = new HashMap<>();
        }

        this.meta.putAll(meta);
    }

    public ServiceDefinition enrich(CamelContext context, ServiceDefinition definition) {
        final DefaultServiceDefinition.Builder builder = DefaultServiceDefinition.builder();

        ObjectHelper.ifNotEmpty(definition, builder::from);
        ObjectHelper.ifNotEmpty(id, builder::withId);
        ObjectHelper.ifNotEmpty(name, builder::withName);
        ObjectHelper.ifNotEmpty(meta, builder::addAllMeta);
        ObjectHelper.ifNotEmpty(host, builder::withHost);

        if (port > 0) {
            builder.withPort(port);
        }

        // if the service does not have an id, we can auto-generate it
        if (builder.id() == null) {
            builder.withId(context.getUuidGenerator().generateUuid());
        }

        return builder.build();
    }
}
