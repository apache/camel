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

/**
 * Constants used in this module
 *
 * @version $Revision$
 */
public interface CxfConstants {
    String METHOD = "method";
    String SERVICE_CLASS = "serviceClass";
    String DATA_FORMAT = "dataFormat";
    String SET_DEFAULT_BUS = "setDefaultBus";
    String WSDL_URL = "wsdlURL";
    String ADDRESS = "address";
    String SERVICE_NAME = "serviceName";
    String PORT_NAME = "portName";
    String SERVICE_LOCALNAME = "serviceLocalName";
    String SERVICE_NAMESPACE = "serviceNamespace";
    String PORT_LOCALNAME = "endpointLocalName";
    String PORT_NAMESPACE = "endpointNamespace";
    String PROTOCOL_NAME_RES = "res";
    String OPERATION_NAME = "operationName";
    String OPERATION_NAMESPACE = "operationNameSpace";
    String SPRING_CONTEXT_ENDPOINT = "bean:";
    String CAMEL_TRANSPORT_PREFIX = "camel:";
    String CXF_EXCHANGE = "org.apache.cxf.message.exchange";
    String CAMEL_EXCHANGE = "org.apache.camel.exchange";
}



