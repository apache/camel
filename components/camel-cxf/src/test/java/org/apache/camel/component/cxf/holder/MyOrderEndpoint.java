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
package org.apache.camel.component.cxf.holder;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;

@WebService
@XmlSeeAlso({MyOrderType.class})
public interface MyOrderEndpoint {
    String myOrder(Holder<String> strPart, int iAmount, Holder<String> strCustomer);
    @RequestWrapper(className = "org.apache.camel.component.cxf.holder.MyOrderType")
    String mySecureOrder(
        @WebParam(name = "iAmount")                 
        int iAmount, 
        @WebParam(mode = WebParam.Mode.INOUT, name = "ENVELOPE_HEADER", header = true)
        Holder<String> envelopeHeader);
}
