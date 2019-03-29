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
package org.apache.camel.component.ribbon;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;

public class RibbonConfiguration {
    private String namespace;
    private String username;
    private String password;
    private IRule rule;
    private IPing ping;
    private String clientName;
    private Map<String, String> properties;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public IRule getRule() {
        return rule;
    }

    public IRule getRuleOrDefault(Supplier<IRule> supplier) {
        return rule != null ? rule : supplier.get();
    }

    public void setRule(IRule rule) {
        this.rule = rule;
    }

    public IPing getPing() {
        return ping;
    }

    public IPing getPingOrDefault(Supplier<IPing> supplier) {
        return ping != null ? ping : supplier.get();
    }

    public void setPing(IPing ping) {
        this.ping = ping;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> clientConfig) {
        this.properties = clientConfig;
    }

    public void addProperty(String key, String value) {
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }

        this.properties.put(key, value);
    }
}
