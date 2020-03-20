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

import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedRecipientListMBean extends ManagedProcessorMBean, ManagedExtendedInformation {

    @ManagedAttribute(description = "The language for the expression")
    String getExpressionLanguage();

    @ManagedAttribute(description = "Expression that returns which endpoints (url) to send the message to (the recipients).", mask = true)
    String getExpression();

    @ManagedAttribute(description = "The uri delimiter to use")
    String getUriDelimiter();

    @ManagedAttribute(description = "Sets the maximum size used by the ProducerCache which is used to cache and reuse producers")
    Integer getCacheSize();

    @ManagedAttribute(description = "If enabled then the aggregate method on AggregationStrategy can be called concurrently.")
    Boolean isParallelAggregate();

    @ManagedAttribute(description = "If enabled then sending messages to the recipient lists occurs concurrently.")
    Boolean isParallelProcessing();

    @ManagedAttribute(description = "If enabled then Camel will process replies out-of-order, eg in the order they come back.")
    Boolean isStreaming();

    @ManagedAttribute(description = "Will now stop further processing if an exception or failure occurred during processing.")
    Boolean isStopOnException();

    @ManagedAttribute(description = "Shares the UnitOfWork with the parent and the resource exchange")
    Boolean isShareUnitOfWork();

    @ManagedAttribute(description = "The total timeout specified in millis, when using parallel processing.")
    Long getTimeout();

    @Override
    @ManagedOperation(description = "Statistics of the endpoints which has been sent to")
    TabularData extendedInformation();

}
