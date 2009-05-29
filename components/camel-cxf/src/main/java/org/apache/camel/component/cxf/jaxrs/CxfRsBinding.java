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

package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.cxf.message.Exchange;

public interface CxfRsBinding {
    
    /**
     * Populate the camel exchange from the CxfRsRequest, the exchange will be consumed 
     * by the processor which the CxfRsConsumer attached.
     * 
     * @param camelExchange  camel exchange object
     * @param cxfExchange  cxf exchange object 
     * @param method  the method which is need for the camel component
     * @param paramArray  the parameter list for the method invocation 
     */
    void populateExchangeFromCxfRsRequest(Exchange cxfExchange,
                              org.apache.camel.Exchange camelExchange,
                              Method method,
                              Object[] paramArray);    
    
    /**
     * Populate the CxfRsResponse object from the camel exchange
     * @param camelExchange  camel exchange object
     * @param cxfExchange  cxf exchange object 
     * @return the response object
     * @throws Exception 
     */
    Object populateCxfRsResponseFromExchange(org.apache.camel.Exchange camelExchange,
                               Exchange cxfExchange) throws Exception;

}
