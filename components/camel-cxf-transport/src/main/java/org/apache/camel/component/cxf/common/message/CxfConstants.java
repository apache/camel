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
package org.apache.camel.component.cxf.common.message;

/**
 * Constants used in this module
 *
 * @version 
 */
public final class CxfConstants {
    public static final String METHOD = "method";
    public static final String SERVICE_CLASS = "serviceClass";
    // CamelCXFDataFormat is used as exchange property key
    public static final String DATA_FORMAT_PROPERTY = "CamelCXFDataFormat";
    public static final String SET_DEFAULT_BUS = "setDefaultBus";
    public static final String WSDL_URL = "wsdlURL";
    public static final String ADDRESS = "address";
    public static final String SERVICE_NAME = "serviceName";
    public static final String PORT_NAME = "portName";
    public static final String SERVICE_LOCALNAME = "serviceLocalName";
    public static final String SERVICE_NAMESPACE = "serviceNamespace";
    public static final String PORT_LOCALNAME = "endpointLocalName";
    public static final String PORT_NAMESPACE = "endpointNamespace";
    public static final String PROTOCOL_NAME_RES = "res";
    public static final String OPERATION_NAME = "operationName";
    public static final String OPERATION_NAMESPACE = "operationNamespace";
    public static final String SPRING_CONTEXT_ENDPOINT = "bean:";
    
    public static final String JAXWS_CONTEXT = "jaxwsContext";
    public static final String DISPATCH_NAMESPACE = "http://camel.apache.org/cxf/jaxws/dispatch";
    public static final String DISPATCH_DEFAULT_OPERATION_NAMESPACE = "Invoke";    
    
    public static final String CAMEL_CXF_MESSAGE = "CamelCxfMessage";
    public static final String CAMEL_CXF_RS_USING_HTTP_API = "CamelCxfRsUsingHttpAPI";
    public static final String CAMEL_CXF_RS_VAR_VALUES = "CamelCxfRsVarValues";
    public static final String CAMEL_CXF_RS_RESPONSE_CLASS = "CamelCxfRsResponseClass";
    public static final String CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE = "CamelCxfRsResponseGenericType";
    public static final String CAMEL_CXF_RS_QUERY_MAP = "CamelCxfRsQueryMap";
    public static final String CAMEL_CXF_RS_EXTRACT_ENTITY = "CamelCxfRsExtractEntity";
    public static final String CAMEL_CXF_RS_OPERATION_RESOURCE_INFO_STACK = "CamelCxfRsOperationResourceInfoStack";
    public static final String CAMEL_CXF_ATTACHMENTS = "CamelAttachments";
    public static final String CAMEL_CXF_RS_THROW_EXCEPTION_ON_FAILURE = "CamelCxfRsThrowExceptionOnFailure";
    
    public static final String WSA_HEADERS_INBOUND = "javax.xml.ws.addressing.context.inbound";

    public static final String WSA_HEADERS_OUTBOUND = "javax.xml.ws.addressing.context.outbound";

    public static final String CAMEL_CXF_PROTOCOL_HEADERS_MERGED = "CamelCxfProtocolHeadersMerged";

    private CxfConstants() {
        // Utility class
    }
}



