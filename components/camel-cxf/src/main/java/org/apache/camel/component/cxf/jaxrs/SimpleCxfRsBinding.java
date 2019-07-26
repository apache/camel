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
package org.apache.camel.component.cxf.jaxrs;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.InputStreamDataSource;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.jaxrs.utils.AnnotationUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.MessageContentsList;

/**
 * A CXF RS Binding which maps method parameters as Camel IN headers and the payload as the IN message body.
 * It replaces the default behaviour of creating a MessageContentsList, which requires the route to process the contents low-level.
 * 
 * <p />
 * 
 * The mapping from CXF to Camel is performed as follows:
 * 
 * <ul>
 *  <li>JAX-RS Parameter types (@QueryParam, @HeaderParam, @CookieParam, @FormParam, @PathParam, @MatrixParam) are all transferred
 *      as IN message headers.</li>
 *  <li>If a request entity is clearly identified (for example, because it's the only parameter without an annotation), it's 
 *      set as the IN message body. Otherwise, the original {@link MessageContentsList} is preserved as the message body.</li>
 *  <li>If Multipart is in use, binary parts are mapped as Camel IN message attachments, while any others are mapped as IN message headers for convenience.
 *      These classes are considered binary: Attachment, DataHandler, DataSource, InputStream. Additionally, the original {@link MessageContentsList} is
 *      preserved as the message body.</li>
 * </ul>
 * 
 * For example, the following JAX-RS method signatures are supported, with the specified outcomes:
 * <p />
 * 
 * <b><tt>public Response doAction(BusinessObject request);</tt></b><br />
 * Request payload is placed in IN message body, replacing the original {@link MessageContentsList}.
 * <p />
 * 
 * <b><tt>public Response doAction(BusinessObject request, @HeaderParam("abcd") String abcd, @QueryParam("defg") String defg);</tt></b><br/>
 * Request payload placed in IN message body, replacing the original {@link MessageContentsList}. 
 * Both request params mapped as IN message headers with names <tt>abcd</tt> and <tt>defg</tt>.
 * <p />
 * 
 * <b><tt>public Response doAction(@HeaderParam("abcd") String abcd, @QueryParam("defg") String defg);</tt></b><br/>
 * Both request params mapped as IN message headers with names <tt>abcd</tt> and <tt>defg</tt>. 
 * The original {@link MessageContentsList} is preserved, even though it only contains the 2 parameters.
 * <p />
 * 
 * <b><tt>public Response doAction(@Multipart(value="body1", type="application/json") BusinessObject request, @Multipart(value="image", 
 *          type="image/jpeg") DataHandler image);</tt></b><br/>
 * The first parameter is transferred as a POJO in a header named <tt>body1</tt>, while the second parameter gets injected as an  
 * attachment with name <tt>image</tt>. The MIME type is observed by the CXF stack. The IN message body is the original 
 * {@link MessageContentsList} handed over from CXF.
 * <p />
 * 
 * <b><tt>public Response doAction(InputStream abcd);</tt></b><br/>
 * The InputStream is unwrapped from the {@link MessageContentsList} and preserved as the IN message body.
 * <p />
 * 
 * <b><tt>public Response doAction(DataHandler abcd);</tt></b><br/>
 * The DataHandler is unwrapped from the {@link MessageContentsList} and preserved as the IN message body.
 */
public class SimpleCxfRsBinding extends DefaultCxfRsBinding {

    /** The JAX-RS annotations to be injected as headers in the IN message */
    private static final Set<Class<?>> HEADER_ANNOTATIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new Class<?>[] {
                CookieParam.class, 
                FormParam.class, 
                PathParam.class,
                HeaderParam.class, 
                MatrixParam.class, 
                QueryParam.class})));
    
    private static final Set<Class<?>> BINARY_ATTACHMENT_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new Class<?>[] {
                Attachment.class,
                DataHandler.class,
                DataSource.class,
                InputStream.class,
            })));
    
    private static final Class<?>[] NO_PARAMETER_TYPES = null;
    private static final Object[] NO_PARAMETERS = null;
    
    /** Caches the Method to Parameters associations to avoid reflection with every request */
    private Map<Method, MethodSpec> methodSpecCache = new ConcurrentHashMap<>();
   

    @Override
    public void populateExchangeFromCxfRsRequest(Exchange cxfExchange, org.apache.camel.Exchange camelExchange,
                                                 Method method, Object[] paramArray) {
        super.populateExchangeFromCxfRsRequest(cxfExchange, camelExchange, method, paramArray);
        Message in = camelExchange.getIn();
        bindHeadersFromSubresourceLocators(cxfExchange, camelExchange);
        MethodSpec spec = methodSpecCache.get(method);
        if (spec == null) {
            spec = MethodSpec.fromMethod(method);
            methodSpecCache.put(method, spec);
        }
        bindParameters(in, paramArray, spec.paramNames, spec.numberParameters);
        bindBody(in, paramArray, spec.entityIndex);
        if (spec.multipart) {
            transferMultipartParameters(paramArray, spec.multipartNames, spec.multipartTypes, in);
        }
    }

    @Override
    public Object populateCxfRsResponseFromExchange(org.apache.camel.Exchange camelExchange, Exchange cxfExchange) throws Exception {
        Object base = super.populateCxfRsResponseFromExchange(camelExchange, cxfExchange);
        return buildResponse(camelExchange, base);
    }

    /**
     * Builds the response for the client.
     * <p />
     * Always returns a JAX-RS {@link Response} object, which gives the user a better control on the response behaviour.
     * If the message body is already an instance of {@link Response}, we reuse it and just inject the relevant HTTP headers.
     * @param camelExchange
     * @param base
     * @return
     */
    protected Object buildResponse(org.apache.camel.Exchange camelExchange, Object base) {
        Message m = camelExchange.getMessage();
        ResponseBuilder response;

        // if the body is different to Response, it's an entity; therefore, check 
        if (base instanceof Response) {
            response = Response.fromResponse((Response) base);
        } else {
            int status = m.getHeader(org.apache.camel.Exchange.HTTP_RESPONSE_CODE, Status.OK.getStatusCode(), Integer.class);
            response = Response.status(status);
            
            // avoid using the request MessageContentsList as the entity; it simply doesn't make sense
            if (base != null && !(base instanceof MessageContentsList)) {
                response.entity(base);
            }
        }

        // Compute which headers to transfer by applying the HeaderFilterStrategy, and transfer them to the JAX-RS Response
        Map<String, String> headersToPropagate = filterCamelHeadersForResponseHeaders(m.getHeaders(), camelExchange);
        for (Entry<String, String> entry : headersToPropagate.entrySet()) {
            response.header(entry.getKey(), entry.getValue());
        }
        return response.build();
    }

    /**
     * Filters the response headers that will be sent back to the client.
     * <p />
     * The {@link DefaultCxfRsBinding} doesn't filter the response headers according to the {@link HeaderFilterStrategy}, 
     * so we handle this task in this binding.
     */
    protected Map<String, String> filterCamelHeadersForResponseHeaders(Map<String, Object> headers,
                                                                     org.apache.camel.Exchange camelExchange) {
        Map<String, String> answer = new HashMap<>();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (getHeaderFilterStrategy().applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), camelExchange)) {
                continue;
            }
            // skip content-length as the simple binding with Response will set correct content-length based
            // on the entity set as the Response
            if ("content-length".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            answer.put(entry.getKey(), entry.getValue().toString());
        }
        return answer;
    }

    /**
     * Transfers path parameters from the full path (including ancestor subresource locators) into Camel IN Message Headers.
     */
    @SuppressWarnings("unchecked")
    protected void bindHeadersFromSubresourceLocators(Exchange cxfExchange, org.apache.camel.Exchange camelExchange) {
        MultivaluedMap<String, String> pathParams = (MultivaluedMap<String, String>) 
                cxfExchange.getInMessage().get(URITemplate.TEMPLATE_PARAMETERS);

        // return immediately if we have no path parameters
        if (pathParams == null || (pathParams.size() == 1 && pathParams.containsKey(URITemplate.FINAL_MATCH_GROUP))) {
            return;
        }

        Message m = camelExchange.getIn();
        for (Entry<String, List<String>> entry : pathParams.entrySet()) {
            // skip over the FINAL_MATCH_GROUP which stores the entire path
            if (URITemplate.FINAL_MATCH_GROUP.equals(entry.getKey())) {
                continue;
            }
            m.setHeader(entry.getKey(), entry.getValue().get(0));
        }
    }

    /**
     * Binds JAX-RS parameter types (@HeaderParam, @QueryParam, @MatrixParam, etc.) to the exchange.
     * 
     * @param in
     * @param paramArray
     * @param paramNames
     * @param numberParameters
     */
    protected void bindParameters(Message in, Object[] paramArray, String[] paramNames, int numberParameters) {
        if (numberParameters == 0) {
            return;
        }
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i] != null) {
                in.setHeader(paramNames[i], paramArray[i]);
            }
        }
    }

    /**
     * Binds the message body.
     * 
     * @param in
     * @param paramArray
     * @param singleBodyIndex
     */
    protected void bindBody(Message in, Object[] paramArray, int singleBodyIndex) {
        if (singleBodyIndex == -1) { 
            return;
        }
        in.setBody(paramArray[singleBodyIndex]);
    }
   
    private void transferMultipartParameters(Object[] paramArray, String[] multipartNames, String[] multipartTypes, Message in) {
        for (int i = 0; i < multipartNames.length; i++) {
            if (multipartNames[i] == null || paramArray[i] == null) {
                continue;
            }
            if (BINARY_ATTACHMENT_TYPES.contains(paramArray[i].getClass())) {
                transferBinaryMultipartParameter(paramArray[i], multipartNames[i], multipartTypes[i], in);
            } else {
                in.setHeader(multipartNames[i], paramArray[i]);
            }
        }
    }

    private void transferBinaryMultipartParameter(Object toMap, String parameterName, String multipartType, Message in) {
        org.apache.camel.attachment.Attachment dh = null;
        if (toMap instanceof Attachment) {
            dh = createCamelAttachment((Attachment) toMap);
        } else if (toMap instanceof DataSource) {
            dh = new DefaultAttachment((DataSource) toMap);
        } else if (toMap instanceof DataHandler) {
            dh = new DefaultAttachment((DataHandler) toMap);
        } else if (toMap instanceof InputStream) {
            dh = new DefaultAttachment(new InputStreamDataSource((InputStream) toMap, multipartType == null ? "application/octet-stream" : multipartType));
        }
        if (dh != null) {
            in.getExchange().getMessage(AttachmentMessage.class).addAttachmentObject(parameterName, dh);
        }
    }

    private DefaultAttachment createCamelAttachment(Attachment attachment) {
        DefaultAttachment camelAttachment = new DefaultAttachment(attachment.getDataHandler());
        for (String name : attachment.getHeaders().keySet()) {
            for (String value : attachment.getHeaderAsList(name)) {
                camelAttachment.addHeader(name, value);
            }
        }
        return camelAttachment;
    }

    protected static class MethodSpec {
        private boolean multipart;
        private int numberParameters;
        private int entityIndex = -1;
        private String[] paramNames;
        private String[] multipartNames;
        private String[] multipartTypes;
        
        /**
         * Processes this method definition and extracts metadata relevant for the binding process.
         * @param method The Method to process.
         * @return A MethodSpec instance representing the method metadata relevant to the Camel binding process.
         */
        public static MethodSpec fromMethod(Method method) {
            method = AnnotationUtils.getAnnotatedMethod(method.getDeclaringClass(), method);
            MethodSpec answer = new MethodSpec();
            
            Annotation[][] annotations = method.getParameterAnnotations();
            int paramCount = method.getParameterTypes().length;
            answer.paramNames = new String[paramCount];
            answer.multipartNames = new String[paramCount];
            answer.multipartTypes = new String[paramCount];
            // remember the names of parameters to be bound to headers and/or attachments
            for (int i = 0; i < paramCount; i++) {
                // if the parameter has no annotations, let its array element remain = null
                for (Annotation a : annotations[i]) {
                    // am I a header?
                    if (HEADER_ANNOTATIONS.contains(a.annotationType())) {
                        try {
                            answer.paramNames[i] = (String) a.annotationType().getMethod("value", NO_PARAMETER_TYPES).invoke(a, NO_PARAMETERS);
                            answer.numberParameters++;
                        } catch (Exception e) { }
                    }
                    
                    // am I multipart?
                    if (Multipart.class.equals(a.annotationType())) {
                        Multipart multipart = (Multipart) a;
                        answer.multipart = true;
                        answer.multipartNames[i] = multipart.value();
                        answer.multipartTypes[i] = multipart.type();
                    }
                }
            }
            
            // if we are not multipart and the number of detected JAX-RS parameters (query, headers, etc.) is less than the number of method parameters
            // there's one parameter that will serve as message body
            if (!answer.multipart && answer.numberParameters < method.getParameterTypes().length) {
                for (int i = 0; i < answer.paramNames.length; i++) {
                    if (answer.paramNames[i] == null) {
                        answer.entityIndex = i;
                        break;
                    }
                }
            }
            
            return answer;
        }

    }
    
}
