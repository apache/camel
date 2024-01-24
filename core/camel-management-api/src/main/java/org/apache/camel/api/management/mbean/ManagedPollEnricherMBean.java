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

public interface ManagedPollEnricherMBean extends ManagedProcessorMBean, ManagedExtendedInformation {

    @ManagedAttribute(description = "The language for the expression")
    String getExpressionLanguage();

    @ManagedAttribute(description = "Expression that computes the endpoint uri to use as the resource endpoint to poll enrich from",
                      mask = true)
    String getExpression();

    @ManagedAttribute(description = "Variable to store the received message body (only body, not headers)")
    String getVariableReceive();

    @ManagedAttribute(description = "Timeout in millis when polling from the external service")
    Long getTimeout();

    @ManagedAttribute(description = "Sets the maximum size used by the ConsumerCache which is used to cache and reuse consumers")
    Integer getCacheSize();

    @ManagedAttribute(description = "Ignore the invalidate endpoint exception when try to create a consumer with that endpoint")
    Boolean isIgnoreInvalidEndpoint();

    @ManagedAttribute(description = "Whether to aggregate when there was an exception thrown during calling the resource endpoint")
    Boolean isAggregateOnException();

    @Override
    @ManagedOperation(description = "Statistics of the endpoints that has been poll enriched from")
    TabularData extendedInformation();

}
