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

public interface ManagedThrottlingExceptionRoutePolicyMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "How long to wait before moving open circuit to half open")
    Long getHalfOpenAfter();

    @ManagedAttribute(description = "How long to wait before moving open circuit to half open")
    void setHalfOpenAfter(Long milliseconds);
    
    @ManagedAttribute(description = "The range of time that failures should occur within")
    Long getFailureWindow();

    @ManagedAttribute(description = "The range of time that failures should occur within")
    void setFailureWindow(Long milliseconds);
    
    @ManagedAttribute(description = "Number of failures before opening circuit")
    Integer getFailureThreshold();

    @ManagedAttribute(description = "Number of failures before opening circuit")
    void setFailureThreshold(Integer numberOfFailures);

    @ManagedOperation(description = "The current state of the circuit")
    String currentState();
    
    @ManagedAttribute(description = "The half open handler registered (if any)")
    String getHalfOpenHandlerName();
    
    @ManagedAttribute(description = "The number of failures caught")
    Integer getCurrentFailures();
    
    @ManagedAttribute(description = "Number of ms since the last failure was recorded")
    Long getLastFailure();
    
    @ManagedAttribute(description = "Number ms since the circuit was opened")
    Long getOpenAt();
}
