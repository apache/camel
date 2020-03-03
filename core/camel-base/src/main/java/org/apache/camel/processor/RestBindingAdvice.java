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
package org.apache.camel.processor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CamelInternalProcessorAdvice} that binds the REST DSL incoming
 * and outgoing messages from sources of json or xml to Java Objects.
 * <p/>
 * The binding uses {@link org.apache.camel.spi.DataFormat} for the actual work to transform
 * from xml/json to Java Objects and reverse again.
 * <p/>
 * The rest producer side is implemented in {@link org.apache.camel.component.rest.RestProducerBindingProcessor}
 *
 * @see CamelInternalProcessor
 */
public class RestBindingAdvice implements CamelInternalProcessorAdvice<Map<String, Object>> {

    private static final Logger LOG = LoggerFactory.getLogger(RestBindingAdvice.class);
    private static final String STATE_KEY_DO_MARSHAL = "doMarshal";
    private static final String STATE_KEY_ACCEPT = "accept";
    private static final String STATE_JSON = "json";
    private static final String STATE_XML = "xml";

    private final AsyncProcessor jsonUnmarshal;
    private final AsyncProcessor xmlUnmarshal;
    private final AsyncProcessor jsonMarshal;
    private final AsyncProcessor xmlMarshal;
    private final String consumes;
    private final String produces;
    private final String bindingMode;
    private final boolean skipBindingOnErrorCode;
    private final boolean clientRequestValidation;
    private final boolean enableCORS;
    private final Map<String, String> corsHeaders;
    private final Map<String, String> queryDefaultValues;
    private final boolean requiredBody;
    private final Set<String> requiredQueryParameters;
    private final Set<String> requiredHeaders;

    public RestBindingAdvice(CamelContext camelContext, DataFormat jsonDataFormat, DataFormat xmlDataFormat,
                             DataFormat outJsonDataFormat, DataFormat outXmlDataFormat,
                             String consumes, String produces, String bindingMode,
                             boolean skipBindingOnErrorCode, boolean clientRequestValidation, boolean enableCORS,
                             Map<String, String> corsHeaders,
                             Map<String, String> queryDefaultValues,
                             boolean requiredBody, Set<String> requiredQueryParameters, Set<String> requiredHeaders) throws Exception {

        if (jsonDataFormat != null) {
            this.jsonUnmarshal = new UnmarshalProcessor(jsonDataFormat);
        } else {
            this.jsonUnmarshal = null;
        }
        if (outJsonDataFormat != null) {
            this.jsonMarshal = new MarshalProcessor(outJsonDataFormat);
        } else if (jsonDataFormat != null) {
            this.jsonMarshal = new MarshalProcessor(jsonDataFormat);
        } else {
            this.jsonMarshal = null;
        }

        if (xmlDataFormat != null) {
            this.xmlUnmarshal = new UnmarshalProcessor(xmlDataFormat);
        } else {
            this.xmlUnmarshal = null;
        }
        if (outXmlDataFormat != null) {
            this.xmlMarshal = new MarshalProcessor(outXmlDataFormat);
        } else if (xmlDataFormat != null) {
            this.xmlMarshal = new MarshalProcessor(xmlDataFormat);
        } else {
            this.xmlMarshal = null;
        }

        if (jsonMarshal != null) {
            camelContext.addService(jsonMarshal, true);
        }
        if (jsonUnmarshal != null) {
            camelContext.addService(jsonUnmarshal, true);
        }
        if (xmlMarshal instanceof CamelContextAware) {
            camelContext.addService(xmlMarshal, true);
        }
        if (xmlUnmarshal instanceof CamelContextAware) {
            camelContext.addService(xmlUnmarshal, true);
        }

        this.consumes = consumes;
        this.produces = produces;
        this.bindingMode = bindingMode;
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
        this.clientRequestValidation = clientRequestValidation;
        this.enableCORS = enableCORS;
        this.corsHeaders = corsHeaders;
        this.queryDefaultValues = queryDefaultValues;
        this.requiredBody = requiredBody;
        this.requiredQueryParameters = requiredQueryParameters;
        this.requiredHeaders = requiredHeaders;
    }
    
    @Override
    public Map<String, Object> before(Exchange exchange) throws Exception {
        Map<String, Object> state = new HashMap<>();
        if (isOptionsMethod(exchange, state)) {
            return state;
        }
        unmarshal(exchange, state);
        return state;
    }
    
    @Override
    public void after(Exchange exchange, Map<String, Object> state) throws Exception {
        if (enableCORS) {
            setCORSHeaders(exchange, state);
        }
        if (state.get(STATE_KEY_DO_MARSHAL) != null) {
            marshal(exchange, state);
        }
    }

    private boolean isOptionsMethod(Exchange exchange, Map<String, Object> state) {
        String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
        if ("OPTIONS".equalsIgnoreCase(method)) {
            // for OPTIONS methods then we should not route at all as its part of CORS
            exchange.setRouteStop(true);
            return true;
        }
        return false;
    }

    private void unmarshal(Exchange exchange, Map<String, Object> state) throws Exception {
        boolean isXml = false;
        boolean isJson = false;

        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            isXml = contentType.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = contentType.toLowerCase(Locale.ENGLISH).contains("json");
        }
        // if content type could not tell us if it was json or xml, then fallback to if the binding was configured with
        // that information in the consumes
        if (!isXml && !isJson) {
            isXml = consumes != null && consumes.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = consumes != null && consumes.toLowerCase(Locale.ENGLISH).contains("json");
        }

        // set data type if in use
        if (exchange.getContext().isUseDataType()) {
            if (exchange.getIn() instanceof DataTypeAware && (isJson || isXml)) {
                ((DataTypeAware) exchange.getIn()).setDataType(new DataType(isJson ? "json" : "xml"));
            }
        }

        // only allow xml/json if the binding mode allows that
        isXml &= bindingMode.equals("auto") || bindingMode.contains("xml");
        isJson &= bindingMode.equals("auto") || bindingMode.contains("json");

        // if we do not yet know if its xml or json, then use the binding mode to know the mode
        if (!isJson && !isXml) {
            isXml = bindingMode.equals("auto") || bindingMode.contains("xml");
            isJson = bindingMode.equals("auto") || bindingMode.contains("json");
        }

        String accept = exchange.getMessage().getHeader("Accept", String.class);
        state.put(STATE_KEY_ACCEPT, accept);

        // perform client request validation
        if (clientRequestValidation) {
            // check if the content-type is accepted according to consumes
            if (!isValidOrAcceptedContentType(consumes, contentType)) {
                LOG.trace("Consuming content type does not match contentType header {}. Stopping routing.", contentType);
                // the content-type is not something we can process so its a HTTP_ERROR 415
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 415);
                // set empty response body as http error code indicate the problem
                exchange.getMessage().setBody(null);
                // stop routing and return
                exchange.setRouteStop(true);
                return;
            }

            // check if what is produces is accepted by the client
            if (!isValidOrAcceptedContentType(produces, accept)) {
                LOG.trace("Produced content type does not match accept header {}. Stopping routing.", contentType);
                // the response type is not accepted by the client so its a HTTP_ERROR 406
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 406);
                // set empty response body as http error code indicate the problem
                exchange.getMessage().setBody(null);
                // stop routing and return
                exchange.setRouteStop(true);
                return;
            }
        }

        String body = null;
        if (exchange.getIn().getBody() != null) {

            // okay we have a binding mode, so need to check for empty body as that can cause the marshaller to fail
            // as they assume a non-empty body
            if (isXml || isJson) {
                // we have binding enabled, so we need to know if there body is empty or not
                // so force reading the body as a String which we can work with
                body = MessageHelper.extractBodyAsString(exchange.getIn());
                if (body != null) {
                    if (exchange.getIn() instanceof DataTypeAware) {
                        ((DataTypeAware)exchange.getIn()).setBody(body, new DataType(isJson ? "json" : "xml"));
                    } else {
                        exchange.getIn().setBody(body);
                    }

                    if (isXml && isJson) {
                        // we have still not determined between xml or json, so check the body if its xml based or not
                        isXml = body.startsWith("<");
                        isJson = !isXml;
                    }
                }
            }
        }

        // add missing default values which are mapped as headers
        if (queryDefaultValues != null) {
            for (Map.Entry<String, String> entry : queryDefaultValues.entrySet()) {
                if (exchange.getIn().getHeader(entry.getKey()) == null) {
                    exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        // check for required
        if (clientRequestValidation) {
            if (requiredBody) {
                // the body is required so we need to know if we have a body or not
                // so force reading the body as a String which we can work with
                if (body == null) {
                    body = MessageHelper.extractBodyAsString(exchange.getIn());
                    if (body != null) {
                        exchange.getIn().setBody(body);
                    }
                }
                if (body == null) {
                    // this is a bad request, the client did not include a message body
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                    exchange.getMessage().setBody("The request body is missing.");
                    // stop routing and return
                    exchange.setRouteStop(true);
                    return;
                }
            }
            if (requiredQueryParameters != null && !exchange.getIn().getHeaders().keySet().containsAll(requiredQueryParameters)) {
                // this is a bad request, the client did not include some of the required query parameters
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                exchange.getMessage().setBody("Some of the required query parameters are missing.");
                // stop routing and return
                exchange.setRouteStop(true);
                return;
            }
            if (requiredHeaders != null && !exchange.getIn().getHeaders().keySet().containsAll(requiredHeaders)) {
                // this is a bad request, the client did not include some of the required http headers
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                exchange.getMessage().setBody("Some of the required HTTP headers are missing.");
                // stop routing and return
                exchange.setRouteStop(true);
                return;
            }
        }

        // favor json over xml
        if (isJson && jsonUnmarshal != null) {
            // add reverse operation
            state.put(STATE_KEY_DO_MARSHAL, STATE_JSON);
            if (ObjectHelper.isNotEmpty(body)) {
                jsonUnmarshal.process(exchange);
                ExchangeHelper.prepareOutToIn(exchange);
            }
            return;
        } else if (isXml && xmlUnmarshal != null) {
            // add reverse operation
            state.put(STATE_KEY_DO_MARSHAL, STATE_XML);
            if (ObjectHelper.isNotEmpty(body)) {
                xmlUnmarshal.process(exchange);
                ExchangeHelper.prepareOutToIn(exchange);
            }
            return;
        }

        // we could not bind
        if ("off".equals(bindingMode) || bindingMode.equals("auto")) {
            // okay for auto we do not mind if we could not bind
            state.put(STATE_KEY_DO_MARSHAL, STATE_JSON);
        } else {
            if (bindingMode.contains("xml")) {
                exchange.setException(new CamelExchangeException("Cannot bind to xml as message body is not xml compatible", exchange));
            } else {
                exchange.setException(new CamelExchangeException("Cannot bind to json as message body is not json compatible", exchange));
            }
        }
        
    }

    private void marshal(Exchange exchange, Map<String, Object> state) {
        // only marshal if there was no exception
        if (exchange.getException() != null) {
            return;
        }

        if (skipBindingOnErrorCode) {
            Integer code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            // if there is a custom http error code then skip binding
            if (code != null && code >= 300) {
                return;
            }
        }

        boolean isXml = false;
        boolean isJson = false;

        // accept takes precedence
        String accept = (String)state.get(STATE_KEY_ACCEPT);
        if (accept != null) {
            isXml = accept.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = accept.toLowerCase(Locale.ENGLISH).contains("json");
        }
        // fallback to content type if still undecided
        if (!isXml && !isJson) {
            String contentType = ExchangeHelper.getContentType(exchange);
            if (contentType != null) {
                isXml = contentType.toLowerCase(Locale.ENGLISH).contains("xml");
                isJson = contentType.toLowerCase(Locale.ENGLISH).contains("json");
            }
        }
        // if content type could not tell us if it was json or xml, then fallback to if the binding was configured with
        // that information in the consumes
        if (!isXml && !isJson) {
            isXml = produces != null && produces.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = produces != null && produces.toLowerCase(Locale.ENGLISH).contains("json");
        }

        // only allow xml/json if the binding mode allows that (when off we still want to know if its xml or json)
        if (bindingMode != null) {
            isXml &= bindingMode.equals("off") || bindingMode.equals("auto") || bindingMode.contains("xml");
            isJson &= bindingMode.equals("off") || bindingMode.equals("auto") || bindingMode.contains("json");

            // if we do not yet know if its xml or json, then use the binding mode to know the mode
            if (!isJson && !isXml) {
                isXml = bindingMode.equals("auto") || bindingMode.contains("xml");
                isJson = bindingMode.equals("auto") || bindingMode.contains("json");
            }
        }

        // in case we have not yet been able to determine if xml or json, then use the same as in the unmarshaller
        if (isXml && isJson) {
            isXml = state.get(STATE_KEY_DO_MARSHAL).equals(STATE_XML);
            isJson = !isXml;
        }

        // need to prepare exchange first
        ExchangeHelper.prepareOutToIn(exchange);

        // ensure there is a content type header (even if binding is off)
        ensureHeaderContentType(produces, isXml, isJson, exchange);

        if (bindingMode == null || "off".equals(bindingMode)) {
            // binding is off, so no message body binding
            return;
        }

        // is there any marshaller at all
        if (jsonMarshal == null && xmlMarshal == null) {
            return;
        }

        // is the body empty
        if (exchange.getMessage().getBody() == null) {
            return;
        }

        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
        // need to lower-case so the contains check below can match if using upper case
        contentType = contentType.toLowerCase(Locale.US);
        try {
            // favor json over xml
            if (isJson && jsonMarshal != null) {
                // only marshal if its json content type
                if (contentType.contains("json")) {
                    jsonMarshal.process(exchange);
                    setOutputDataType(exchange, new DataType("json"));
                }
            } else if (isXml && xmlMarshal != null) {
                // only marshal if its xml content type
                if (contentType.contains("xml")) {
                    xmlMarshal.process(exchange);
                    setOutputDataType(exchange, new DataType("xml"));
                }
            } else {
                // we could not bind
                if (bindingMode.equals("auto")) {
                    // okay for auto we do not mind if we could not bind
                } else {
                    if (bindingMode.contains("xml")) {
                        exchange.setException(new CamelExchangeException("Cannot bind to xml as message body is not xml compatible", exchange));
                    } else {
                        exchange.setException(new CamelExchangeException("Cannot bind to json as message body is not json compatible", exchange));
                    }
                }
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }
    }

    private void setOutputDataType(Exchange exchange, DataType type) {
        Message target = exchange.getMessage();
        if (target instanceof DataTypeAware) {
            ((DataTypeAware)target).setDataType(type);
        }
    }

    private void ensureHeaderContentType(String contentType, boolean isXml, boolean isJson, Exchange exchange) {
        // favor given content type
        if (contentType != null) {
            String type = ExchangeHelper.getContentType(exchange);
            if (type == null) {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, contentType);
            }
        }

        // favor json over xml
        if (isJson) {
            // make sure there is a content-type with json
            String type = ExchangeHelper.getContentType(exchange);
            if (type == null) {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
            }
        } else if (isXml) {
            // make sure there is a content-type with xml
            String type = ExchangeHelper.getContentType(exchange);
            if (type == null) {
                exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/xml");
            }
        }
    }

    private void setCORSHeaders(Exchange exchange, Map<String, Object> state) {
        // add the CORS headers after routing, but before the consumer writes the response
        Message msg = exchange.getMessage();

        // use default value if none has been configured
        String allowOrigin = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Origin") : null;
        if (allowOrigin == null) {
            allowOrigin = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_ORIGIN;
        }
        String allowMethods = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Methods") : null;
        if (allowMethods == null) {
            allowMethods = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_METHODS;
        }
        String allowHeaders = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Headers") : null;
        if (allowHeaders == null) {
            allowHeaders = RestConfiguration.CORS_ACCESS_CONTROL_ALLOW_HEADERS;
        }
        String maxAge = corsHeaders != null ? corsHeaders.get("Access-Control-Max-Age") : null;
        if (maxAge == null) {
            maxAge = RestConfiguration.CORS_ACCESS_CONTROL_MAX_AGE;
        }
        String allowCredentials = corsHeaders != null ? corsHeaders.get("Access-Control-Allow-Credentials") : null;

        // Restrict the origin if credentials are allowed.
        // https://www.w3.org/TR/cors/ - section 6.1, point 3
        String origin = exchange.getIn().getHeader("Origin", String.class);
        if ("true".equalsIgnoreCase(allowCredentials) && "*".equals(allowOrigin) && origin != null) {
            allowOrigin = origin;
        }

        msg.setHeader("Access-Control-Allow-Origin", allowOrigin);
        msg.setHeader("Access-Control-Allow-Methods", allowMethods);
        msg.setHeader("Access-Control-Allow-Headers", allowHeaders);
        msg.setHeader("Access-Control-Max-Age", maxAge);
        if (allowCredentials != null) {
            msg.setHeader("Access-Control-Allow-Credentials", allowCredentials);
        }
    }

    private static boolean isValidOrAcceptedContentType(String valid, String target) {
        if (valid == null || target == null) {
            return true;
        }

        // Any MIME type
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept#Directives
        if ("*/*".equals(target)) {
            return true;
        }

        boolean isXml = valid.toLowerCase(Locale.ENGLISH).contains("xml");
        boolean isJson = valid.toLowerCase(Locale.ENGLISH).contains("json");

        String type = target.toLowerCase(Locale.ENGLISH);

        if (isXml && !type.contains("xml")) {
            return false;
        }
        if (isJson && !type.contains("json")) {
            return false;
        }

        return true;
    }

}
