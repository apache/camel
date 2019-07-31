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
package org.apache.camel.component.metrics.messagehistory;

import org.apache.camel.api.management.ManagedOperation;

public interface MetricsMessageHistoryMBean {

    @ManagedOperation(description = "Dumps the statistics as json")
    String dumpStatisticsAsJson();

    @ManagedOperation(description = "Dumps the statistics as json using seconds for time units")
    String dumpStatisticsAsJsonTimeUnitSeconds();

    @ManagedOperation(description = "Reset all counters")
    void reset();

}
