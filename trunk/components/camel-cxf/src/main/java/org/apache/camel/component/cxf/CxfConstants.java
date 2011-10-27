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
 * Please use the org.apache.camel.component.cxf.common.message.CxfConstants in the camel-cxf-transport module
 * This class will be removed in Camel 3.0
 */
@Deprecated
public interface CxfConstants {
    String METHOD = "method";
    String SERVICE_CLASS = "serviceClass";
    // CamelCXFDataFormat is used as exchange property key
    String DATA_FORMAT_PROPERTY = "CamelCXFDataFormat";
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
    String OPERATION_NAMESPACE = "operationNamespace";
    String SPRING_CONTEXT_ENDPOINT = "bean:";
    @Deprecated
    // This constants will be removed in Camel 3.0
    // Please use org.apache.camel.component.cxf.transport.CamelTransportConstants in camel-cxf-transport
    String CAMEL_TRANSPORT_PREFIX = "camel:";
    String JAXWS_CONTEXT = "jaxwsContext";
    @Deprecated
    // This constants will be removed in Camel 3.0
    // Please use org.apache.camel.component.cxf.transport.CamelTransportConstants in camel-cxf-transport
    String CXF_EXCHANGE = "org.apache.cxf.message.exchange";
    String DISPATCH_NAMESPACE = "http://camel.apache.org/cxf/jaxws/dispatch";
    String DISPATCH_DEFAULT_OPERATION_NAMESPACE = "Invoke";    
    @Deprecated
    // This constants will be removed in Camel 3.0
    // Please use org.apache.camel.component.cxf.transport.CamelTransportConstants in camel-cxf-transport
    String CAMEL_EXCHANGE = "org.apache.camel.exchange";
    String CAMEL_CXF_MESSAGE = "CamelCxfMessage";
    String CAMEL_CXF_RS_USING_HTTP_API = "CamelCxfRsUsingHttpAPI";
    String CAMEL_CXF_RS_VAR_VALUES = "CamelCxfRsVarValues";
    String CAMEL_CXF_RS_RESPONSE_CLASS = "CamelCxfRsResponseClass";
    String CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE = "CamelCxfRsResponseGenericType";
    String CAMEL_CXF_RS_QUERY_MAP = "CamelCxfRsQueryMap";
    String CAMEL_CXF_RS_EXTRACT_ENTITY = "CamelCxfRsExtractEntity";
    String CAMEL_CXF_RS_OPERATION_RESOURCE_INFO_STACK = "CamelCxfRsOperationResourceInfoStack";
    String CAMEL_CXF_ATTACHMENTS = "CamelAttachments";
    String CAMEL_CXF_RS_THROW_EXCEPTION_ON_FAILURE = "CamelCxfRsThrowExceptionOnFailure";

}



