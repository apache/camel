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

public interface ManagedResequencerMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "Expression to use for re-ordering the messages, such as a header with a sequence number")
    String getExpression();

    @ManagedAttribute(description = "The size of the batch to be re-ordered. The default size is 100.")
    Integer getBatchSize();

    @ManagedAttribute(description = "Minimum time to wait for missing elements (messages).")
    Long getTimeout();

    @ManagedAttribute(description = "Whether to allow duplicates.")
    Boolean isAllowDuplicates();

    @ManagedAttribute(description = "Whether to reverse the ordering.")
    Boolean isReverse();

    @ManagedAttribute(description = "Whether to ignore invalid exchanges")
    Boolean isIgnoreInvalidExchanges();

    @ManagedAttribute(description = "The capacity of the resequencer's inbound queue")
    Integer getCapacity();

    @ManagedAttribute(description = "If true, throws an exception when messages older than the last delivered message are processed")
    Boolean isRejectOld();

}