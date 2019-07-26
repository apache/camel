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
package org.apache.camel.component.rest;

import java.io.InputStream;
import java.util.Locale;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link org.apache.camel.Processor} that binds the REST producer request and reply messages
 * from sources of json or xml to Java Objects.
 * <p/>
 * The binding uses {@link org.apache.camel.spi.DataFormat} for the actual work to transform
 * from xml/json to Java Objects and reverse again.
 * <p/>
 * The rest-dsl consumer side is implemented in {@link org.apache.camel.processor.RestBindingAdvice}
 */
public class RestProducerBindingProcessor extends DelegateAsyncProcessor {

    private final CamelContext camelContext;
    private final AsyncProcessor jsonUnmarshal;
    private final AsyncProcessor xmlUnmarshal;
    private final AsyncProcessor jsonMarshal;
    private final AsyncProcessor xmlMarshal;
    private final String bindingMode;
    private final boolean skipBindingOnErrorCode;
    private final String outType;

    public RestProducerBindingProcessor(AsyncProcessor processor, CamelContext camelContext,
                                        DataFormat jsonDataFormat, DataFormat xmlDataFormat,
                                        DataFormat outJsonDataFormat, DataFormat outXmlDataFormat,
                                        String bindingMode, boolean skipBindingOnErrorCode,
                                        String outType) {

        super(processor);

        this.camelContext = camelContext;

        if (outJsonDataFormat != null) {
            this.jsonUnmarshal = new UnmarshalProcessor(outJsonDataFormat);
        } else {
            this.jsonUnmarshal = null;
        }
        if (jsonDataFormat != null) {
            this.jsonMarshal = new MarshalProcessor(jsonDataFormat);
        } else {
            this.jsonMarshal = null;
        }

        if (outXmlDataFormat != null) {
            this.xmlUnmarshal = new UnmarshalProcessor(outXmlDataFormat);
        } else {
            this.xmlUnmarshal = null;
        }
        if (xmlDataFormat != null) {
            this.xmlMarshal = new MarshalProcessor(xmlDataFormat);
        } else {
            this.xmlMarshal = null;
        }

        this.bindingMode = bindingMode;
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
        this.outType = outType;
    }

    @Override
    public String toString() {
        return "RestProducerBindingProcessor";
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
        ServiceHelper.startService(jsonMarshal, jsonUnmarshal, xmlMarshal, xmlUnmarshal);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(jsonMarshal, jsonUnmarshal, xmlMarshal, xmlUnmarshal);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean isXml = false;
        boolean isJson = false;

        // skip before binding for empty/null body
        Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            if (outType != null) {
                // wrap callback to add reverse operation if we know the output type from the REST service
                callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, false);
            }
            // okay now we can continue routing to the producer
            return getProcessor().process(exchange, callback);
        }

        // we only need to perform before binding if the message body is POJO based
        if (body instanceof String || body instanceof byte[]) {
            // the body is text based and thus not POJO so no binding needed
            if (outType != null) {
                // wrap callback to add reverse operation if we know the output type from the REST service
                callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, false);
            }
            // okay now we can continue routing to the producer
            return getProcessor().process(exchange, callback);
        } else {
            // if its convertable to stream based then its not POJO based
            InputStream is = camelContext.getTypeConverter().tryConvertTo(InputStream.class, exchange, body);
            if (is != null) {
                exchange.getIn().setBody(is);
                if (outType != null) {
                    // wrap callback to add reverse operation if we know the output type from the REST service
                    callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, false);
                }
                // okay now we can continue routing to the producer
                return getProcessor().process(exchange, callback);
            }
        }

        // assume body is POJO based and binding needed

        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            isXml = contentType.toLowerCase(Locale.ENGLISH).contains("xml");
            isJson = contentType.toLowerCase(Locale.ENGLISH).contains("json");
        }

        // only allow xml/json if the binding mode allows that
        isXml &= bindingMode.equals("auto") || bindingMode.contains("xml");
        isJson &= bindingMode.equals("auto") || bindingMode.contains("json");

        // if we do not yet know if its xml or json, then use the binding mode to know the mode
        if (!isJson && !isXml) {
            isXml = bindingMode.equals("auto") || bindingMode.contains("xml");
            isJson = bindingMode.equals("auto") || bindingMode.contains("json");
        }

        // favor json over xml
        if (isJson && jsonMarshal != null) {
            try {
                jsonMarshal.process(exchange);
            } catch (Exception e) {
                // we failed so cannot call producer
                exchange.setException(e);
                callback.done(true);
                return true;
            }
            // need to prepare exchange first
            ExchangeHelper.prepareOutToIn(exchange);
            if (outType != null) {
                // wrap callback to add reverse operation if we know the output type from the REST service
                callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, false);
            }
            // okay now we can continue routing to the producer
            return getProcessor().process(exchange, callback);
        } else if (isXml && xmlMarshal != null) {
            try {
                xmlMarshal.process(exchange);
            } catch (Exception e) {
                // we failed so cannot call producer
                exchange.setException(e);
                callback.done(true);
                return true;
            }
            // need to prepare exchange first
            ExchangeHelper.prepareOutToIn(exchange);
            if (outType != null) {
                // wrap callback to add reverse operation if we know the output type from the REST service
                callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, true);
            }
            // okay now we can continue routing to the producer
            return getProcessor().process(exchange, callback);
        }

        // we could not bind
        if ("off".equals(bindingMode) || bindingMode.equals("auto")) {
            if (outType != null) {
                // wrap callback to add reverse operation if we know the output type from the REST service
                callback = new RestProducerBindingUnmarshalCallback(exchange, callback, jsonMarshal, xmlMarshal, false);
            }
            // okay now we can continue routing to the producer
            return getProcessor().process(exchange, callback);
        } else {
            if (bindingMode.contains("xml")) {
                exchange.setException(new CamelExchangeException("Cannot bind to xml as message body is not xml compatible", exchange));
            } else {
                exchange.setException(new CamelExchangeException("Cannot bind to json as message body is not json compatible", exchange));
            }
            // we failed so cannot call producer
            callback.done(true);
            return true;
        }
    }

    private final class RestProducerBindingUnmarshalCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final AsyncProcessor jsonMarshal;
        private final AsyncProcessor xmlMarshal;
        private boolean wasXml;

        private RestProducerBindingUnmarshalCallback(Exchange exchange, AsyncCallback callback,
                                                     AsyncProcessor jsonMarshal, AsyncProcessor xmlMarshal, boolean wasXml) {
            this.exchange = exchange;
            this.callback = callback;
            this.jsonMarshal = jsonMarshal;
            this.xmlMarshal = xmlMarshal;
            this.wasXml = wasXml;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                doDone();
            } catch (Throwable e) {
                exchange.setException(e);
            } finally {
                // ensure callback is called
                callback.done(doneSync);
            }
        }

        private void doDone() {
            // only unmarshal if there was no exception
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

            // check the content-type if its json or xml
            String contentType = ExchangeHelper.getContentType(exchange);
            if (contentType != null) {
                isXml = contentType.toLowerCase(Locale.ENGLISH).contains("xml");
                isJson = contentType.toLowerCase(Locale.ENGLISH).contains("json");
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
            ensureHeaderContentType(isXml, isJson, exchange);

            if (bindingMode == null || "off".equals(bindingMode)) {
                // binding is off, so no message body binding
                return;
            }

            // is there any unmarshaller at all
            if (jsonUnmarshal == null && xmlUnmarshal == null) {
                return;
            }

            // is the body empty
            if ((exchange.hasOut() && exchange.getOut().getBody() == null) || (!exchange.hasOut() && exchange.getIn().getBody() == null)) {
                return;
            }

            contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            // need to lower-case so the contains check below can match if using upper case
            contentType = contentType.toLowerCase(Locale.US);
            try {
                // favor json over xml
                if (isJson && jsonUnmarshal != null) {
                    // only marshal if its json content type
                    if (contentType.contains("json")) {
                        jsonUnmarshal.process(exchange);
                    }
                } else if (isXml && xmlUnmarshal != null) {
                    // only marshal if its xml content type
                    if (contentType.contains("xml")) {
                        xmlUnmarshal.process(exchange);
                    }
                } else {
                    // we could not bind
                    if (bindingMode.equals("auto")) {
                        // okay for auto we do not mind if we could not bind
                    } else {
                        if (bindingMode.contains("xml")) {
                            exchange.setException(new CamelExchangeException("Cannot bind from xml as message body is not xml compatible", exchange));
                        } else {
                            exchange.setException(new CamelExchangeException("Cannot bind from json as message body is not json compatible", exchange));
                        }
                    }
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
        }

        private void ensureHeaderContentType(boolean isXml, boolean isJson, Exchange exchange) {
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
            return "RestProducerBindingUnmarshalCallback";
        }
    }

}
