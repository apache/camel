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

import org.apache.camel.api.management.ManagedOperation;

public interface ManagedBrowsableEndpointMBean extends ManagedEndpointMBean {

    @ManagedOperation(description = "Current number of Exchanges in Queue")
    long queueSize();

    @ManagedOperation(description = "Get Exchange from queue by index")
    String browseExchange(Integer index);

    @ManagedOperation(description = "Get message body from queue by index")
    String browseMessageBody(Integer index);

    /**
     * @deprecated use {@link #browseAllMessagesAsXml(Boolean)} instead
     */
    @ManagedOperation(description = "Get message as XML from queue by index")
    @Deprecated
    String browseMessageAsXml(Integer index);

    @ManagedOperation(description = "Get message as XML from queue by index")
    String browseMessageAsXml(Integer index, Boolean includeBody);

    @ManagedOperation(description = "Gets all the messages as XML from the queue")
    String browseAllMessagesAsXml(Boolean includeBody);

    @ManagedOperation(description = "Gets the range of messages as XML from the queue")
    String browseRangeMessagesAsXml(Integer fromIndex, Integer toIndex, Boolean includeBody);

}