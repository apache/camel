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
package org.apache.camel.impl.cloud;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;

public class CombinedServiceDiscovery implements ServiceDiscovery {
    private final List<ServiceDiscovery> delegates;

    public CombinedServiceDiscovery(List<ServiceDiscovery> delegates) {
        this.delegates = Collections.unmodifiableList(delegates);
    }

    public List<ServiceDiscovery> getDelegates() {
        return this.delegates;
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        return delegates.stream()
            .flatMap(d -> d.getServices(name).stream())
            .collect(Collectors.toList());
    }

    // **********************
    // Helpers
    // **********************

    public static CombinedServiceDiscovery wrap(ServiceDiscovery... delegates) {
        return new CombinedServiceDiscovery(Arrays.asList(delegates));
    }
}
