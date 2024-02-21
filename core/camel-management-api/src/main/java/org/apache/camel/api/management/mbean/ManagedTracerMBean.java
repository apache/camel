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

public interface ManagedTracerMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Is tracing standby")
    boolean isStandby();

    @ManagedAttribute(description = "Is tracing enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "Is tracing enabled")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "To filter tracing by nodes (pattern)")
    void setTracePattern(String pattern);

    @ManagedAttribute(description = "To filter tracing by nodes (pattern)")
    String getTracePattern();

    @ManagedAttribute(description = "Whether tracing routes created from Rest DSL.")
    boolean isTraceRests();

    @ManagedAttribute(description = "Whether tracing routes created from route templates or kamelets.")
    boolean isTraceTemplates();

    @ManagedAttribute(description = "Number of total traced messages")
    long getTraceCounter();

    @ManagedOperation(description = "Resets the trace counter")
    void resetTraceCounter();

}
