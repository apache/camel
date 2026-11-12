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
    // This is here to smooth the transition from old Camel exchange header
    public static final String ACCEPT_CONTENT_TYPE = Message.ACCEPT_CONTENT_TYPE;

    @Metadata(description = "The name of the operation.", javaType = "String")
    public static final String OPERATION_NAME = "CamelCxfOperationName";
    @Metadata(description = "The namespace of the operation named by the CamelCxfOperationName header or by"
                            + " defaultOperationName. The producer uses it in place of defaultOperationNamespace to find the"
                            + " operation to invoke. The consumer sets it to the namespace of the operation of the request. It"
                            + " is not set when dataFormat is RAW.",
              javaType = "String", applicableFor = SCHEME_CXF)
    public static final String OPERATION_NAMESPACE = "CamelCxfOperationNamespace";
    public static final String SPRING_CONTEXT_ENDPOINT = "bean:";
    @Metadata(description = "The address to send the request to, in place of the service address. The producer sets it as the"
                            + " CXF property org.apache.cxf.message.Message.ENDPOINT_ADDRESS of the CXF request message. The"
                            + " HTTP transport uses it, and the Camel transport does not.",
              javaType = "String", applicableFor = SCHEME_CXF)
    public static final String DESTINATION_OVERRIDE_URL = Exchange.DESTINATION_OVERRIDE_URL;
    @Metadata(description = "The CXF response context, as a Map. The producer sets it on the Camel message of the response. It"
                            + " holds the CXF properties of the CXF response message, for example the response code. The"
                            + " consumer copies its entries to the CXF response message, but exchange properties and values"
                            + " from the Camel message headers replace entries with the same key. The entry"
                            + " org.apache.cxf.message.Message.PROTOCOL_HEADERS is not used, because the consumer builds the"
                            + " protocol headers from the Camel message headers.",
              javaType = "Map<String, Object>", applicableFor = SCHEME_CXF)
    public static final String RESPONSE_CONTEXT = Client.RESPONSE_CONTEXT;
    @Metadata(description = "The authenticated user of the request, as a Subject. The consumer sets it from the"
                            + " SecurityContext of the CXF request message: the Subject of a LoginSecurityContext, or else a"
                            + " new Subject with the user Principal.",
              javaType = "javax.security.auth.Subject")
    public static final String AUTHENTICATION = Exchange.AUTHENTICATION;
    @Metadata(description = "The entries to add to the CXF request context, as a Map. The producer copies them to the CXF"
                            + " request context, but exchange properties and values from the Camel message headers replace"
                            + " entries with the same key. The entry org.apache.cxf.message.Message.PROTOCOL_HEADERS is not"
                            + " used, because the producer builds the protocol headers from the Camel message headers.",
              javaType = "Object", applicableFor = SCHEME_CXF)
    public static final String REQUEST_CONTEXT = Client.REQUEST_CONTEXT;

    public static final String JAXWS_CONTEXT = "jaxwsContext";
    public static final String DISPATCH_NAMESPACE = "http://camel.apache.org/cxf/jaxws/dispatch";
    public static final String DISPATCH_DEFAULT_OPERATION_NAMESPACE = "Invoke";

    @Metadata(description = "The HTTP method of the request, for example GET. The consumer sets it to the method of the"
                            + " request. With the WebClient API, the producer sends its request with this method, and without"
                            + " an entity for GET.",
              javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;
    @Metadata(description = "The path of the request below the service address, for example /customers/123. The consumer sets"
                            + " it to the path of the request below the service address. With the WebClient API, the producer"
                            + " appends it to the service address or to the address in the CamelDestinationOverrideUrl header,"
                            + " and fills template variables such as {id} in this path with the values of the"
                            + " CamelCxfRsVarValues header.",
              javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(description = "The Content-Type of the message body. The consumer sets it to the Content-Type of the request,"
                            + " and uses it as the Content-Type of the response when the message body of the reply is not a"
                            + " jakarta.ws.rs.core.Response. The consumer does not use this header for the response when"
                            + " synchronous is true or when it gets no continuation from the transport. The producer sends it"
                            + " as the Content-Type of its request. When synchronous is false, or when the producer reads the"
                            + " response as a Response, it copies the response headers to the reply, and a Content-Type of the"
                            + " response replaces this header.",
              javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(description = "The query string of the request, without the question mark, for example id=1&name=a%20b. The"
                            + " consumer sets it when the request has a query string. With the WebClient API, the producer"
                            + " sends the parameters of this query string in place of the query parameters of the endpoint URI"
                            + " that are not options, unless the CamelCxfRsQueryMap header is set.",
              javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(description = "The HTTP status code of the response. The producer sets it to the status code of the response,"
                            + " unless the exchange pattern is InOnly or the exchange fails, which with the default settings"
                            + " happens for a status code greater than 207. On the consumer, the route sets it to choose the"
                            + " status code of the response when the message body of the reply is not a"
                            + " jakarta.ws.rs.core.Response. If not set, the status code of the response is 200 for a"
                            + " successful reply, or 204 for a reply without a message body when bindingStyle is Default.",
              javaType = "Integer", applicableFor = SCHEME_CXF_RS)
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(description = "The Content-Encoding of the message body, for example gzip. The consumer sets it to the"
                            + " Content-Encoding of the request, unless CXF has already decompressed the request, for example"
                            + " with a GZIPFeature. The producer sends it as the Content-Encoding of its request. When"
                            + " synchronous is false, or when the producer reads the response as a"
                            + " jakarta.ws.rs.core.Response, it copies the response headers to the reply, and a"
                            + " Content-Encoding of the response replaces this header, unless CXF has already decompressed the"
                            + " response. The consumer and the producer do not compress or decompress the message body because"
                            + " of this header.",
              javaType = "String", applicableFor = SCHEME_CXF_RS)
    public static final String CONTENT_ENCODING = Exchange.CONTENT_ENCODING;
    @Metadata(description = "The HTTP headers to add to the response of the consumer, as a Map from the header name to a List"
                            + " of values or to one value. The consumer adds them when the message body of the reply is not a"
                            + " jakarta.ws.rs.core.Response, and the Content-Type header replaces the Content-Type entry of"
                            + " the Map. The consumer ignores the entries of the Map when synchronous is true or when it gets"
                            + " no continuation from the transport. The consumer does not put the HTTP headers of the request"
                            + " into this Map: each HTTP header of the request that the header filter strategy keeps becomes a"
                            + " separate Camel header.",
              javaType = "Map", applicableFor = SCHEME_CXF_RS)
    public static final String PROTOCOL_HEADERS = Message.PROTOCOL_HEADERS;
    @Metadata(description = "The CXF request message, as an org.apache.cxf.message.Message. The consumer sets it for each"
                            + " request. With the WebClient API, the producer adds the matrix parameters in the request URI of"
                            + " this message to the end of the path of its own request.",
              javaType = "org.apache.cxf.message.Message", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_MESSAGE = "CamelCxfMessage";
    @Metadata(description = "Whether to use the CXF WebClient API instead of the proxy-based API for this message. This header"
                            + " replaces the httpClientAPI option.",
              javaType = "Boolean", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_USING_HTTP_API = "CamelCxfRsUsingHttpAPI";
    @Metadata(description = "The values of the template variables in the path of the request, as an Object[], in the order in"
                            + " which the variables appear. With the WebClient API, the producer fills the variables such as"
                            + " {id} in the CamelHttpPath header with them. With the proxy-based API, the producer passes them"
                            + " to CXF for the variables in the Path annotation of the first resource class, and a PathParam"
                            + " or BeanParam parameter of the resource method can take precedence for one of these variables.",
              javaType = "Object[]", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_VAR_VALUES = "CamelCxfRsVarValues";
    @Metadata(description = "The class to read the response entity as, for example String. The consumer sets it to the return"
                            + " type of the resource method. The producer uses it only with the WebClient API, where the"
                            + " message body of the reply is the jakarta.ws.rs.core.Response if this header is not set or is"
                            + " Response. When synchronous is false, this header is a class other than Response and the"
                            + " CamelCxfRsResponseGenericType header is set, the producer reads the response entity as that"
                            + " generic type instead. When synchronous is true and this header is a class other than Response,"
                            + " CXF fails the exchange for a status code of 300 or more, except 304, regardless of"
                            + " throwExceptionOnFailure.",
              javaType = "Class", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_RESPONSE_CLASS = "CamelCxfRsResponseClass";
    @Metadata(description = "The generic type to read the response entity as, given as a java.lang.reflect.Type. The consumer"
                            + " sets it to the generic return type of the resource method. The producer uses it only with the"
                            + " WebClient API, and only when the CamelCxfRsResponseClass header is a class other than"
                            + " jakarta.ws.rs.core.Response. When synchronous is true, the producer uses it only for a"
                            + " Collection response class, and this header must then be a ParameterizedType whose first type"
                            + " argument is the class of the elements.",
              javaType = "Type", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_RESPONSE_GENERIC_TYPE = "CamelCxfRsResponseGenericType";
    @Metadata(description = "The query parameters of the request, as a Map from the parameter name to the value. With the"
                            + " WebClient API, the producer sends them in place of the CamelHttpQuery header and of the query"
                            + " parameters of the endpoint URI that are not options, even when the Map is empty.",
              javaType = "Map<String, String>", applicableFor = SCHEME_CXF_RS)
    public static final String CAMEL_CXF_RS_QUERY_MAP = "CamelCxfRsQueryMap";
    public static final String CAMEL_CXF_RS_EXTRACT_ENTITY = "CamelCxfRsExtractEntity";
    @Metadata(description = "The resource methods and sub-resource locators that CXF matched for the request, as a copy of the"
                            + " org.apache.cxf.jaxrs.model.OperationResourceInfoStack, which holds one MethodInvocationInfo"
                            + " for each of them, with the root resource at the bottom of the stack. The consumer sets it for"
                            + " each request. The producer does not use it.",
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
