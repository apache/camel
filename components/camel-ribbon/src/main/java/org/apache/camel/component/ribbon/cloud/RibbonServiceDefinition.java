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
package org.apache.camel.component.ribbon.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.netflix.loadbalancer.Server;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceHealth;
import org.apache.camel.impl.cloud.DefaultServiceHealth;
import org.apache.camel.util.ObjectHelper;

public class RibbonServiceDefinition extends Server implements ServiceDefinition {
    private static final ServiceHealth DEFAULT_SERVICE_HEALTH = new DefaultServiceHealth();

    private String name;
    private ServiceHealth health;
    private Map<String, String> metaData;

    public RibbonServiceDefinition(String name, String host, int port) {
        this(name, host, port, null, DEFAULT_SERVICE_HEALTH);
    }

    public RibbonServiceDefinition(String name, String host, int port, ServiceHealth health) {
        this(name, host, port, null, health);
    }

    public RibbonServiceDefinition(String name, String host, int port,  Map<String, String> meta) {
        this(name, host, port, meta, DEFAULT_SERVICE_HEALTH);
    }

    public RibbonServiceDefinition(String name, String host, int port, Map<String, String> meta, ServiceHealth health) {
        super(host, port);
        this.name = name;
        this.metaData = meta;
        this.health = health;
    }

    public RibbonServiceDefinition(ServiceDefinition definition) {
        this(
            definition.getName(),
            definition.getHost(),
            definition.getPort(),
            definition.getMetadata(),
            definition.getHealth()
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHost() {
        return super.getHost();
    }

    @Override
    public int getPort() {
        return super.getPort();
    }

    @Override
    public ServiceHealth getHealth() {
        return health;
    }

    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> meta = metaData != null ? new HashMap<>(metaData) : new HashMap<>();
        ObjectHelper.ifNotEmpty(super.getId(), val -> meta.put("id", val));
        ObjectHelper.ifNotEmpty(super.getZone(), val -> meta.put("zone", val));
        ObjectHelper.ifNotEmpty(super.isAlive(), val -> meta.put("is_alive", Boolean.toString(val)));
        ObjectHelper.ifNotEmpty(super.isReadyToServe(), val -> meta.put("ready_to_server", Boolean.toString(val)));

        return Collections.unmodifiableMap(meta);
    }
}
