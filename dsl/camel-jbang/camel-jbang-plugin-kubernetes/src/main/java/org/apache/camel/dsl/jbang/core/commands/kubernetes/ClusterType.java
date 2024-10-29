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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.Arrays;

/**
 * Known default cluster types.
 */
public enum ClusterType {
    KUBERNETES,
    OPENSHIFT,
    KIND,
    K3S,
    MINIKUBE;

    public static ClusterType fromName(String name) {
        return Arrays.stream(values())
                .filter(ct -> ct.name().equalsIgnoreCase(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown cluster type: %s".formatted(name)));
    }

    public boolean isEqualTo(String clusterType) {
        if (clusterType == null) {
            return false;
        }

        return this == fromName(clusterType);
    }
}
