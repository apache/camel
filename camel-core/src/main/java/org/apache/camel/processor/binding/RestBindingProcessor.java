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
package org.apache.camel.processor.binding;

import java.util.Locale;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Route;
import org.apache.camel.processor.MarshalProcessor;
import org.apache.camel.processor.UnmarshalProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A {@link org.apache.camel.Processor} that binds the REST DSL incoming and outgoing messages
 * from sources of json or xml to Java Objects.
 * <p/>
 * The binding uses {@link org.apache.camel.spi.DataFormat} for the actual work to transform
 * from xml/json to Java Objects and reverse again.
 */
public class RestBindingProcessor extends ServiceSupport implements AsyncProcessor {

    private final CamelContext camelContext;
    private final AsyncProcessor jsonUnmarshal;
    private final AsyncProcessor xmlUnmarshal;
    private final AsyncProcessor jsonMarshal;
    private final AsyncProcessor xmlMarshal;
    private final String consumes;
    private final String produces;
    private final String bindingMode;
    private final boolean skipBindingOnErrorCode;
    private final boolean enableCORS;
    private final Map<String, String> corsHeaders;

    public RestBindingProcessor(CamelContext camelContext, DataFormat jsonDataFormat, DataFormat xmlDataFormat,
                                DataFormat outJsonDataFormat, DataFormat outXmlDataFormat,
                                String consumes, String produces, String bindingMode,
                                boolean skipBindingOnErrorCode, boolean enableCORS,
                                Map<String, String> corsHeaders) {

        this.camelContext = camelContext;

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

        this.consumes = consumes;
        this.produces = produces;
        this.bindingMode = bindingMode;
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
        this.enableCORS = enableCORS;
        this.corsHeaders = corsHeaders;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (enableCORS) {
            exchange.addOnCompletion(new RestBindingCORSOnCompletion(corsHeaders));
        }

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

        // only allow xml/json if the binding mode allows that
        isXml &= bindingMode.equals("auto") || bindingMode.contains("xml");
        isJson &= bindingMode.equals("auto") || bindingMode.contains("json");

        // if we do not yet know if its xml or json, then use the binding mode to know the mode
        if (!isJson && !isXml) {
            isXml = bindingMode.equals("auto") || bindingMode.contains("xml");
            isJson = bindingMode.equals("auto") || bindingMode.contains("json");
        }

        String accept = exchange.getIn().getHeader("Accept", String.class);

        String body = null;
        if (exchange.getIn().getBody() != null) {

           // okay we have a binding mode, so need to check for empty body as that can cause the marshaller to fail
            // as they assume a non-empty body
            if (isXml || isJson) {
                // we have binding enabled, so we need to know if there body is empty or not\
                // so force reading the body as a String which we can work with
                body = MessageHelper.extractBodyAsString(exchange.getIn());
                if (body != null) {
                    exchange.getIn().setBody(body);

                    if (isXml && isJson) {
                        // we have still not determined between xml or json, so check the body if its xml based or not
                        isXml = body.startsWith("<");
                        isJson = !isXml;
                    }
                }
            }
        }

        // favor json over xml
        if (isJson && jsonUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(exchange.getFromRouteId(), jsonMarshal, xmlMarshal, false, accept));
            if (ObjectHelper.isNotEmpty(body)) {
                return jsonUnmarshal.process(exchange, callback);
            } else {
                callback.done(true);
                return true;
            }
        } else if (isXml && xmlUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(exchange.getFromRouteId(), jsonMarshal, xmlMarshal, true, accept));
            if (ObjectHelper.isNotEmpty(body)) {
                return xmlUnmarshal.process(exchange, callback);
            } else {
                callback.done(true);
                return true;
            }
        }

        // we could not bind
        if (bindingMode == null || "off".equals(bindingMode) || bindingMode.equals("auto")) {
            // okay for auto we do not mind if we could not bind
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(exchange.getFromRouteId(), jsonMarshal, xmlMarshal, false, accept));
            callback.done(true);
            return true;
        } else {
            if (bindingMode.contains("xml")) {
                exchange.setException(new BindingException("Cannot bind to xml as message body is not xml compatible", exchange));
            } else {
                exchange.setException(new BindingException("Cannot bind to json as message body is not json compatible", exchange));
            }
            callback.done(true);
            return true;
        }
    }

    @Override
    public String toString() {
        return "RestBindingProcessor";
    }

    @Override
    protected void doStart() throws Exception {
        // inject CamelContext before starting
        if (jsonMarshal instanceof CamelContextAware) {
            ((CamelContextAware) jsonMarshal).setCamelContext(camelContext);
        }
        if (jsonUnmarshal instanceof CamelContextAware) {
            ((CamelContextAware) jsonUnmarshal).setCamelContext(camelContext);
        }
        if (xmlMarshal instanceof CamelContextAware) {
            ((CamelContextAware) xmlMarshal).setCamelContext(camelContext);
        }
        if (xmlUnmarshal instanceof CamelContextAware) {
            ((CamelContextAware) xmlUnmarshal).setCamelContext(camelContext);
        }
        ServiceHelper.startServices(jsonMarshal, jsonUnmarshal, xmlMarshal, xmlUnmarshal);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(jsonMarshal, jsonUnmarshal, xmlMarshal, xmlUnmarshal);
    }

    /**
     * An {@link org.apache.camel.spi.Synchronization} that does the reverse operation
     * of marshalling from POJO to json/xml
     */
    private final class RestBindingMarshalOnCompletion extends SynchronizationAdapter {

        private final AsyncProcessor jsonMarshal;
        private final AsyncProcessor xmlMarshal;
        private final String routeId;
        private boolean wasXml;
        private String accept;

        private RestBindingMarshalOnCompletion(String routeId, AsyncProcessor jsonMarshal, AsyncProcessor xmlMarshal, boolean wasXml, String accept) {
            this.routeId = routeId;
            this.jsonMarshal = jsonMarshal;
            this.xmlMarshal = xmlMarshal;
            this.wasXml = wasXml;
            this.accept = accept;
        }

        @Override
        public void onAfterRoute(Route route, Exchange exchange) {
            // we use the onAfterRoute callback, to ensure the data has been marshalled before
            // the consumer writes the response back

            // only trigger when it was the 1st route that was done
            if (!routeId.equals(route.getId())) {
                return;
            }

            // only marshal if there was no exception
            if (exchange.getException() != null) {
                return;
            }

            if (skipBindingOnErrorCode) {
                Integer code = exchange.hasOut() ? exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class) : exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                // if there is a custom http error code then skip binding
                if (code != null && code >= 300) {
                    return;
                }
            }

            boolean isXml = false;
            boolean isJson = false;

            // accept takes precedence
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
                isXml = wasXml;
                isJson = !wasXml;
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
            if ((exchange.hasOut() && exchange.getOut().getBody() == null) || (!exchange.hasOut() && exchange.getIn().getBody() == null)) {
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
                    }
                } else if (isXml && xmlMarshal != null) {
                    // only marshal if its xml content type
                    if (contentType.contains("xml")) {
                        xmlMarshal.process(exchange);
                    }
                } else {
                    // we could not bind
                    if (bindingMode.equals("auto")) {
                        // okay for auto we do not mind if we could not bind
                    } else {
                        if (bindingMode.contains("xml")) {
                            exchange.setException(new BindingException("Cannot bind to xml as message body is not xml compatible", exchange));
                        } else {
                            exchange.setException(new BindingException("Cannot bind to json as message body is not json compatible", exchange));
                        }
                    }
                }
            } catch (Throwable e) {
                exchange.setException(e);
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

        @Override
        public String toString() {
            return "RestBindingMarshalOnCompletion";
        }
    }

    private final class RestBindingCORSOnCompletion extends SynchronizationAdapter {

        private final Map<String, String> corsHeaders;

        private RestBindingCORSOnCompletion(Map<String, String> corsHeaders) {
            this.corsHeaders = corsHeaders;
        }

        @Override
        public void onAfterRoute(Route route, Exchange exchange) {
            // add the CORS headers after routing, but before the consumer writes the response
            Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();

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

            msg.setHeader("Access-Control-Allow-Origin", allowOrigin);
            msg.setHeader("Access-Control-Allow-Methods", allowMethods);
            msg.setHeader("Access-Control-Allow-Headers", allowHeaders);
            msg.setHeader("Access-Control-Max-Age", maxAge);
        }

        @Override
        public String toString() {
            return "RestBindingCORSOnCompletion";
        }
    }

}
