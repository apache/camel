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
package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

public final class ServiceBuilder {
    private Boolean auto;
    private Boolean enabled;
    private Service.Type type;

    private ServiceBuilder() {
    }

    public static ServiceBuilder service() {
        return new ServiceBuilder();
    }

    public ServiceBuilder withAuto(Boolean auto) {
        this.auto = auto;
        return this;
    }

    public ServiceBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ServiceBuilder withType(Service.Type type) {
        this.type = type;
        return this;
    }

    public Service build() {
        Service service = new Service();
        service.setAuto(auto);
        service.setEnabled(enabled);
        service.setType(type);
        return service;
    }
}
