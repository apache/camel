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

import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedCircuitBreakerLoadBalancerMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "Number of processors in the load balancer")
    Integer getSize();

    @ManagedAttribute(description = "The timeout in millis to use as threshold to move state from closed to half-open or open state")
    Long getHalfOpenAfter();

    @ManagedAttribute(description = "Number of previous failed messages to use as threshold to move state from closed to half-open or open state")
    Integer getThreshold();

    @ManagedAttribute(description = "The class names of the exceptions the load balancer uses (separated by comma)")
    String getExceptions();

    @ManagedAttribute(description = "The current state of the circuit breaker")
    String getCircuitBreakerState();

    @ManagedOperation(description = "Dumps the state of the load balancer")
    String dumpState();

    @ManagedOperation(description = "Statistics of the content based router for each exception")
    TabularData exceptionStatistics();

}