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
package org.apache.camel.component.cxf;

import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;

/**
 * A Default Service Endpoint Interface (aka serviceClass) to be used if neither explicit 
 * serviceClass and WSDL is specified in a CXF endpoint
 */
@SOAPBinding(parameterStyle = ParameterStyle.BARE)
@WebService(targetNamespace = "http://camel.apache.org/cxf/jaxws/dispatch", name = "DefaultSEI")
public interface DefaultSEI {

    @Oneway
    @WebMethod(operationName = "InvokeOneWay")
    void invokeOneWay(@WebParam(partName = "InvokeOneWayRequest",  mode = WebParam.Mode.IN)String in);
    
    @WebMethod(operationName = "Invoke")
    void invoke(@WebParam(partName = "InvokeRequest", mode = WebParam.Mode.IN)String in,
                   @WebParam(partName = "InvokeResponse",  mode = WebParam.Mode.OUT)String out);
}
