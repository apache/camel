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
package org.apache.camel.component.cxf.common.message;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.message.Message;

/**
 * Constants used in this module
 */
public final class CxfConstants {

    // The schemes
    public static final String SCHEME_CXF = "cxf";
    public static final String SCHEME_CXF_RS = "cxfrs";

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

    @Metadata(description = "The name of the operation.", javaType = "String")
    public static final String OPERATION_NAME = "operationName";
    @Metadata(description = "The operation namespace.", javaType = "String", applicableFor = SCHEME_CXF)
    public static final String OPERATION_NAMESPACE = "operationNamespace";
    public static final String SPRING_CONTEXT_ENDPOINT = "bean:";
    @Metadata(description = "The destination override url", javaType = "String", applicableFor = SCHEME_CXF)
    public static final String DESTINATION_OVERRIDE_URL = Exchange.DESTINATION_OVERRIDE_URL;
    @Metadata(description = "The response context", javaType = "Map<String, Object>", applicableFor = SCHEME_CXF)
    public static final String RESPONSE_CONTEXT = Client.RESPONSE_CONTEXT;
    @Metadata(description = "The authentication", javaType = "javax.security.auth.Subject")
    public static final String AUTHENTICATION = Exchange.AUTHENTICATION;
    @Metadata(description = "The request context", javaType = "Object", applicableFor = SCHEME_CXF)
    public static final String REQUEST_CONTEXT = Client.REQUEST_CONTEXT;

    public static final String JAXWS_CONTEXT = "jaxwsContext";
    public static final String DISPATCH_NAMESPACE = "http://camel.apache.org/cxf/jaxws/dispatch";
    public static final String DISPATCH_DEFAULT_OPERATION_NAMESPACE = "Invoke";

    @Metadata(description = "The http method to use", javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(description = "The http path", javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The content type", javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "The http query", javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(description = "The http response code", javaType = "Integer", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The content encoding", javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String CONTENT_ENCODING = Exchange.CONTENT_ENCODING;
    @Metadata(description = "The protocol headers", javaType = "Map", applicableFor = SCHEME_CXF_RS)
    public static final String PROTOCOL_HEADERS = Message.PROTOCOL_HEADERS;
    @Metadata(description = "The CXF message", javaType = "org.apache.cxf.message.Message", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_MESSAGE = "CamelCxfMessage";
    @Metadata(description = "If it is true, the CxfRsProducer will use the HttpClientAPI to invoke the service. If it is false, the\n"
                            +
                            " CxfRsProducer will use the ProxyClientAPI to invoke the service",
              javaType = "Boolean", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_USING_HTTP_API = "CamelCxfRsUsingHttpAPI";
    @Metadata(description = "The path values", javaType = "Object[]", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_VAR_VALUES = "CamelCxfRsVarValues";
    @Metadata(description = "The response class", javaType = "Class", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_RESPONSE_CLASS = "CamelCxfRsResponseClass";
    @Metadata(description = "The response generic type", javaType = "Type", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE = "CamelCxfRsResponseGenericType";
    @Metadata(description = "The query map", javaType = "Map<String, String>", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_QUERY_MAP = "CamelCxfRsQueryMap";
    public static final String CAMEL_CXF_RS_EXTRACT_ENTITY = "CamelCxfRsExtractEntity";
    @Metadata(description = "The stack of MethodInvocationInfo representing resources path when JAX-RS invocation looks for target.",
              javaType = "org.apache.cxf.jaxrs.model.OperationResourceInfoStack", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_OPERATION_RESOURCE_INFO_STACK = "CamelCxfRsOperationResourceInfoStack";
    public static final String CAMEL_CXF_ATTACHMENTS = "CamelAttachments";
    public static final String CAMEL_CXF_RS_THROW_EXCEPTION_ON_FAILURE = "CamelCxfRsThrowExceptionOnFailure";

    public static final String WSA_HEADERS_INBOUND = "jakarta.xml.ws.addressing.context.inbound";

    public static final String WSA_HEADERS_OUTBOUND = "jakarta.xml.ws.addressing.context.outbound";

    public static final String CAMEL_CXF_PROTOCOL_HEADERS_MERGED = "CamelCxfProtocolHeadersMerged";

    private CxfConstants() {
        // Utility class
    }
}
