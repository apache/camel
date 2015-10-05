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

public interface ManagedProcessorMBean extends ManagedPerformanceCounterMBean {

    @ManagedAttribute(description = "Processor State")
    String getState();

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Route ID")
    String getRouteId();

    @ManagedAttribute(description = "Processor ID")
    String getProcessorId();

    @ManagedAttribute(description = "Processor Index")
    Integer getIndex();

    @ManagedAttribute(description = "Whether this processor supports extended JMX information")
    Boolean getSupportExtendedInformation();

    @ManagedOperation(description = "Start Processor")
    void start() throws Exception;

    @ManagedOperation(description = "Stop Processor")
    void stop() throws Exception;

    @ManagedOperation(description = "Processor information as JSon")
    String informationJson();

    @ManagedOperation(description = "Explain how this processor is configured")
    TabularData explain(boolean allOptions);

    @ManagedOperation(description = "Dumps the processor as XML")
    String dumpProcessorAsXml() throws Exception;

}