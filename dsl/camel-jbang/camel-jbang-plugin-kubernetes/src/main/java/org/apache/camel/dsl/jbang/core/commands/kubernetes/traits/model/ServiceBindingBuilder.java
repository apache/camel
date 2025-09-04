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

import java.util.List;

public final class ServiceBindingBuilder {
    private Boolean enabled;
    private List<String> services;

    private ServiceBindingBuilder() {
    }

    public static ServiceBindingBuilder serviceBinding() {
        return new ServiceBindingBuilder();
    }

    public ServiceBindingBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ServiceBindingBuilder withServices(List<String> services) {
        this.services = services;
        return this;
    }

    public ServiceBinding build() {
        ServiceBinding serviceBinding = new ServiceBinding();
        serviceBinding.setEnabled(enabled);
        serviceBinding.setServices(services);
        return serviceBinding;
    }
}
