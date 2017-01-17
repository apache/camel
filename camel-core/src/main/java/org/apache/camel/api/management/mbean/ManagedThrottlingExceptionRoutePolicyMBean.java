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

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedThrottlingExceptionRoutePolicyMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "how long to wait before moving open circuit to half open")
    long getHalfOpenAfter();

    @ManagedAttribute(description = "how long to wait before moving open circuit to half open")
    void setHalfOpenAfter(long milliseconds);
    
    @ManagedAttribute(description = "the range of time that failures should occur within")
    long getFailureWindow();

    @ManagedAttribute(description = "the range of time that failures should occur within")
    void setFailureWindow(long milliseconds);
    
    @ManagedAttribute(description = "number of failures before opening circuit")
    int getFailureThreshold();

    @ManagedAttribute(description = "number of failures before opening circuit")
    void setFailureThreshold(int numberOfFailures);

    @ManagedAttribute(description = "State")
    String currentState();
    
    @ManagedAttribute(description = "The half open handler registered (if any)")
    String hasHalfOpenHandler();
    
    @ManagedAttribute(description = "the number of failures caught")
    int currentFailures();
    
    @ManagedAttribute(description = "number of ms since the last failure was recorded")
    long getLastFailure();
    
    @ManagedAttribute(description = "number ms since the circuit was opened")
    long getOpenAt();
}
