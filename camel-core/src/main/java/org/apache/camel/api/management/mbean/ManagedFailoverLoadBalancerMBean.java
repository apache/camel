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

public interface ManagedFailoverLoadBalancerMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "Number of processors in the load balancer")
    Integer getSize();

    @ManagedAttribute(description = "Whether or not the failover load balancer should operate in round robin mode or not.")
    Boolean isRoundRobin();

    @ManagedAttribute(description = "Whether or not the failover load balancer should operate in sticky mode or not.")
    Boolean isSticky();

    @ManagedAttribute(description = "A value to indicate after X failover attempts we should exhaust (give up).")
    Integer getMaximumFailoverAttempts();

    @ManagedAttribute(description = "The class names of the exceptions the load balancer uses (separated by comma)")
    String getExceptions();

    @ManagedAttribute(description = "Processor id of the last known good processor that succeed processing the exchange")
    String getLastGoodProcessorId();

    @ManagedOperation(description = "Statistics of the content based router for each exception")
    TabularData exceptionStatistics();

}