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

public interface ManagedRoutingSlipMBean extends ManagedProcessorMBean, ManagedExtendedInformation {

    @ManagedAttribute(description = "The language for the expression")
    String getExpressionLanguage();

    @ManagedAttribute(description = "Expression to define the routing slip, which defines which endpoints to route the message in a pipeline style.", mask = true)
    String getExpression();

    @ManagedAttribute(description = "The uri delimiter to use")
    String getUriDelimiter();

    @ManagedAttribute(description = "Sets the maximum size used by the ProducerCache which is used to cache and reuse producers")
    Integer getCacheSize();

    @ManagedAttribute(description = "Ignore the invalidate endpoint exception when try to create a producer with that endpoint")
    Boolean isIgnoreInvalidEndpoints();

    @ManagedOperation(description = "Statistics of the endpoints which has been sent to")
    TabularData extendedInformation();

}