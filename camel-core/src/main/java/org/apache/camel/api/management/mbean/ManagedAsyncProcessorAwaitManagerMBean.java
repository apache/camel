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

public interface ManagedAsyncProcessorAwaitManagerMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Whether to interrupt any blocking threads during stopping.")
    boolean isInterruptThreadsWhileStopping();

    @ManagedAttribute(description = "Whether to interrupt any blocking threads during stopping.")
    void setInterruptThreadsWhileStopping(boolean interruptThreadsWhileStopping);

    @ManagedAttribute(description = "Number of threads that are blocked waiting for other threads to trigger the callback when they are done processing the exchange")
    int getSize();

    @ManagedOperation(description = "Lists all the exchanges which are currently inflight, having a blocked thread awaiting for other threads to trigger the callback when they are done")
    TabularData browse();

    @ManagedOperation(description = "To interrupt an exchange which may seem as stuck, to force the exchange to continue, allowing any blocking thread to be released.")
    void interrupt(String exchangeId);

}
