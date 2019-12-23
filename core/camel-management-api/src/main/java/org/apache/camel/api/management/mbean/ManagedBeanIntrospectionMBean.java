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

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedBeanIntrospectionMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Number of times bean introspection has been invoked")
    Long getInvokedCounter();

    @ManagedAttribute(description = "Whether to gather extended statistics for introspection usage")
    Boolean isExtendedStatistics();

    @ManagedAttribute(description = "Whether to gather extended statistics for introspection usage")
    void setExtendedStatistics(Boolean extendedStatistics);

    @ManagedOperation(description = "Rests the statistic counters")
    void resetCounters();

    @ManagedAttribute(description = "Number of cached introspected bean classes")
    Long getCachedClasses();

    @ManagedOperation(description = "Clears the cache for introspected bean classes")
    void clearCache();

}
