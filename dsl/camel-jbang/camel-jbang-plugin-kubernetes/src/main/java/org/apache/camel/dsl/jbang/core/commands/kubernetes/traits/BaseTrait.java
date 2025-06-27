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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import org.apache.camel.dsl.jbang.core.commands.kubernetes.ClusterType;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;

public abstract class BaseTrait implements Trait {

    public static final String KUBERNETES_LABEL_NAME = "app.kubernetes.io/name";
    public static final String KUBERNETES_LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";

    private final String id;
    private final int order;

    public BaseTrait(String id) {
        this(id, 1000);
    }

    public BaseTrait(String id, int order) {
        this.id = id;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean accept(ClusterType clusterType) {
        return true;
    }

    @Override
    public void applyRuntimeSpecificProperties(Traits traitConfig, TraitContext context, RuntimeType runtimeType) {
    }
}
