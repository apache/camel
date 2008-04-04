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

import org.apache.cxf.message.Exchange;

/**
 * The interface to provide a CXF message invoke method
 */
public interface MessageInvoker {

    /**
     * This method is called when the incoming message is to be passed into the
     * camel processor. The return value is the response from the processor
     *
     * @param exchange the CXF exchange which holds the in and out message
     */
    void invoke(Exchange exchange);

}
