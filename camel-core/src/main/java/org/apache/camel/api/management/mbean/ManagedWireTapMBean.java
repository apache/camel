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

public interface ManagedWireTapMBean extends ManagedProcessorMBean, ManagedExtendedInformation {

    @ManagedAttribute(description = "The uri of the endpoint to wiretap to. The uri can be dynamic computed using the expressions.", mask = true)
    String getUri();

    @ManagedAttribute(description = "Sets the maximum size used by the ProducerCache which is used to cache and reuse producers")
    Integer getCacheSize();

    @ManagedAttribute(description = "Ignore the invalidate endpoint exception when try to create a producer with that endpoint")
    Boolean isIgnoreInvalidEndpoint();

    @ManagedAttribute(description = "Uses a copy of the original exchange")
    Boolean isCopy();

    @ManagedAttribute(description = "Current size of inflight wire tapped exchanges.")
    Integer getTaskSize();

    @ManagedOperation(description = "Statistics of the endpoints which has been sent to")
    TabularData extendedInformation();

}