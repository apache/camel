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
package org.apache.camel.component.kubernetes.cloud;

import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;

/**
 * Discovers where services are running on which servers in Kubernetes.
 */
public abstract class KubernetesServiceDiscovery extends DefaultServiceDiscovery {
    private final KubernetesConfiguration configuration;

    public KubernetesServiceDiscovery(KubernetesConfiguration configuration) {
        this.configuration = configuration;
    }

    protected KubernetesConfiguration getConfiguration() {
        return this.configuration;
    }
}
