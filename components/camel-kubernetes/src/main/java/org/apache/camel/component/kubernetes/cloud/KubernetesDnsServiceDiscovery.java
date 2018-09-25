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

package org.apache.camel.component.kubernetes.cloud;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.util.ObjectHelper;

public class KubernetesDnsServiceDiscovery extends KubernetesServiceDiscovery {
    private final ConcurrentMap<String, List<ServiceDefinition>> cache;
    private final String namespace;
    private final String zone;

    public KubernetesDnsServiceDiscovery(KubernetesConfiguration configuration) {
        super(configuration);

        this.namespace = configuration.getNamespace() != null ? configuration.getNamespace() : System.getenv("KUBERNETES_NAMESPACE");
        this.zone = configuration.getDnsDomain();

        // validation
        ObjectHelper.notNull(namespace, "Namespace");
        ObjectHelper.notNull(zone, "DNS Domain");

        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        return this.cache.computeIfAbsent(name, key -> Collections.singletonList(newService(name)));
    }

    private ServiceDefinition newService(String name) {
        return new DefaultServiceDefinition(
            name,
            name + "." + getConfiguration().getNamespace() + ".svc." + getConfiguration().getDnsDomain(),
            -1);
    }

    @Override
    public String toString() {
        return "KubernetesDnsServiceDiscovery{"
            + "namespace='" + namespace + '\''
            + ", zone='" + zone + '\''
            + '}';
    }
}
