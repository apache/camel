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
package org.apache.camel.api.management.mbean;

import java.util.List;
import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedRuntimeEndpointRegistryMBean extends ManagedServiceMBean {

    @ManagedOperation(description = "Clears the registry")
    void clear();

    @ManagedOperation(description = "Reset the statistic counters")
    void reset();

    @ManagedAttribute(description = "Whether gathering runtime usage is enabled or not")
    boolean isEnabled();

    @ManagedAttribute(description = "Whether gathering runtime usage is enabled or not")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "Maximum number of endpoints to keep in the cache per route")
    int getLimit();

    @ManagedAttribute(description = "Number of endpoints currently in the registry")
    int getSize();

    @ManagedOperation(description = " Gets all the endpoint urls captured during runtime that are in-use")
    List<String> getAllEndpoints(boolean includeInputs);

    @ManagedOperation(description = " Gets all the endpoint urls captured during runtime that are in-use for the given route")
    List<String> getEndpointsPerRoute(String routeId, boolean includeInputs);

    @ManagedOperation(description = "Lists statistics about all the endpoints in the registry")
    TabularData endpointStatistics();

}
