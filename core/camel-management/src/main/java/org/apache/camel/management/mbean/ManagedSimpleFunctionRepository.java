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
package org.apache.camel.management.mbean;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedSimpleFunctionRegistryMBean;
import org.apache.camel.spi.SimpleFunctionRegistry;

@ManagedResource(description = "Managed SimpleFunctionRegistry")
public class ManagedSimpleFunctionRepository extends ManagedService implements ManagedSimpleFunctionRegistryMBean {

    private final SimpleFunctionRegistry registry;

    public ManagedSimpleFunctionRepository(CamelContext context, SimpleFunctionRegistry simpleFunctionRegistry) {
        super(context, simpleFunctionRegistry);
        this.registry = simpleFunctionRegistry;
    }

    @Override
    public int getSize() {
        return registry.size();
    }

    @Override
    public Set<String> getFunctionNames() {
        return registry.getFunctionNames();
    }

    @Override
    public boolean hasFunction(String name) {
        return registry.getFunction(name) != null;
    }
}
