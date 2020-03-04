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
package org.apache.camel.api.management.mbean;

import java.util.Collection;

import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedCamelHealthMBean {
    
    @ManagedAttribute(description = "Application Health")
    boolean getIsHealthy();

    @ManagedAttribute(description = "Registered Health Checks IDs")
    Collection<String> getHealthChecksIDs();

    @ManagedOperation(description = "Registered Health Checks Details")
    TabularData details();

    @ManagedOperation(description = "Invoke an Health Check by ID")
    String invoke(String id);
}
