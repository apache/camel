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

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedVariableRepositoryMBean;
import org.apache.camel.spi.VariableRepository;

@ManagedResource(description = "Managed VariableRepository")
public class ManagedVariableRepository extends ManagedService implements ManagedVariableRepositoryMBean {
    private final VariableRepository variableRepository;

    public ManagedVariableRepository(CamelContext context, VariableRepository variableRepository) {
        super(context, variableRepository);
        this.variableRepository = variableRepository;
    }

    public VariableRepository getVariableRepository() {
        return variableRepository;
    }

    @Override
    public String getId() {
        return variableRepository.getId();
    }
}
