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
package org.apache.camel.component.cxf;

import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.message.Message;

/**
 * The default CXF header filter strategy.
 * 
 * @version $Revision$
 */
public class CxfHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public CxfHeaderFilterStrategy() {
        initialize();  
    }

    protected void initialize() {
        getOutFilter().add(CxfConstants.OPERATION_NAME);
        getOutFilter().add(CxfConstants.OPERATION_NAMESPACE);
        
        // Request and response context Maps will be passed to CXF Client APIs
        getOutFilter().add(Client.REQUEST_CONTEXT);
        getOutFilter().add(Client.RESPONSE_CONTEXT);

        // protocol headers are stored as a Map.  DefaultCxfBinding
        // read the Map and send each entry to the filter.  Therefore,
        // we need to filter the header of this name.
        getOutFilter().add(Message.PROTOCOL_HEADERS);
        getInFilter().add(Message.PROTOCOL_HEADERS);
    }

}
