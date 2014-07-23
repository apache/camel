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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.processor.MarshalProcessor;
import org.apache.camel.processor.UnmarshalProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;

/**
 * A {@link org.apache.camel.Processor} that binds the REST DSL incoming and outgoing messages
 * from sources of json or xml to Java Objects.
 * <p/>
 * The binding uses {@link org.apache.camel.spi.DataFormat} for the actual work to transform
 * from xml/json to Java Objects and reverse again.
 */
public class RestBindingProcessor extends ServiceSupport implements AsyncProcessor {

    private final AsyncProcessor jsonUnmarshal;
    private final AsyncProcessor xmlUnmarshal;
    private final AsyncProcessor jsonMmarshal;
    private final AsyncProcessor xmlMmarshal;
    private final String consumes;
    private final String produces;
    private final String bindingMode;

    public RestBindingProcessor(DataFormat jsonDataFormat, DataFormat xmlDataFormat, String consumes, String produces, String bindingMode) {
        this.jsonUnmarshal = jsonDataFormat != null ? new UnmarshalProcessor(jsonDataFormat) : null;
        this.jsonMmarshal = jsonDataFormat != null ? new MarshalProcessor(jsonDataFormat) : null;
        this.xmlUnmarshal = xmlDataFormat != null ? new UnmarshalProcessor(xmlDataFormat) : null;
        this.xmlMmarshal = xmlDataFormat != null ? new MarshalProcessor(xmlDataFormat) : null;
        this.consumes = consumes;
        this.produces = produces;
        this.bindingMode = bindingMode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    // TODO: consumes/produces can be a list of media types, and prioritized 1st to last.
    // TODO: parsing body should only be done if really needed

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (bindingMode == null || "off".equals(bindingMode)) {
            // binding is off
            callback.done(true);
            return true;
        }

        // is there any unmarshaller at all
        if (jsonUnmarshal == null && xmlUnmarshal == null) {
            callback.done(true);
            return true;
        }

        boolean isXml = false;
        boolean isJson = false;

        // content type takes precedence, over consumes
        String contentType = ExchangeHelper.getContentType(exchange);
        if (contentType != null) {
            isXml = contentType.toLowerCase(Locale.US).contains("xml");
            isJson = contentType.toLowerCase(Locale.US).contains("json");
        }
        // if content type could not tell us if it was json or xml, then fallback to if the binding was configured with
        // that information in the consumes
        if (!isXml && !isJson) {
            isXml = consumes != null && consumes.toLowerCase(Locale.US).contains("xml");
            isJson = consumes != null && consumes.toLowerCase(Locale.US).contains("json");
        }

        if (!isXml && !isJson) {
            // read the content into memory so we can determine if its xml or json
            String body = MessageHelper.extractBodyAsString(exchange.getIn());
            isXml = body.startsWith("<") || body.contains("xml");
            isJson = !isXml;
        }

        // only allow xml/json if the binding mode allows that
        isXml &= bindingMode.equals("auto") || bindingMode.contains("xml");
        isJson &= bindingMode.equals("auto") || bindingMode.contains("json");

        if (isXml && xmlUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal));
            return xmlUnmarshal.process(exchange, callback);
        } else if (isJson && jsonUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal));
            return jsonUnmarshal.process(exchange, callback);
        }

        // we could not bind
        if (bindingMode.equals("auto")) {
            // okay for auto we do not mind if we could not bind
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
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * An {@link org.apache.camel.spi.Synchronization} that does the reverse operation
     * of marshalling from POJO to json/xml
     */
    private final class RestBindingMarshalOnCompletion extends SynchronizationAdapter {

        private final AsyncProcessor jsonMmarshal;
        private final AsyncProcessor xmlMmarshal;

        private RestBindingMarshalOnCompletion(AsyncProcessor jsonMmarshal, AsyncProcessor xmlMmarshal) {
            this.jsonMmarshal = jsonMmarshal;
            this.xmlMmarshal = xmlMmarshal;
        }

        @Override
        public void onComplete(Exchange exchange) {
            // only marshal if we succeeded

            boolean isXml = false;
            boolean isJson = false;

            String contentType = ExchangeHelper.getContentType(exchange);
            if (contentType != null) {
                isXml = contentType.toLowerCase(Locale.US).contains("xml");
                isJson = contentType.toLowerCase(Locale.US).contains("json");
            }
            // if content type could not tell us if it was json or xml, then fallback to if the binding was configured with
            // that information in the consumes
            if (!isXml && !isJson) {
                isXml = produces != null && produces.toLowerCase(Locale.US).contains("xml");
                isJson = produces != null && produces.toLowerCase(Locale.US).contains("json");
            }

            // need to prepare exchange first
            ExchangeHelper.prepareOutToIn(exchange);

            if (!isXml && !isJson) {
                // read the content into memory so we can determine if its xml or json
                String body = MessageHelper.extractBodyAsString(exchange.getIn());
                isXml = body.startsWith("<") || body.contains("xml");
                isJson = !isXml;
            }

            // only allow xml/json if the binding mode allows that
            isXml &= bindingMode.equals("auto") || bindingMode.contains("xml");
            isJson &= bindingMode.equals("auto") || bindingMode.contains("json");

            try {
                if (isXml && xmlMmarshal != null) {
                    xmlMmarshal.process(exchange);
                } else if (isJson && jsonMmarshal != null) {
                    jsonMmarshal.process(exchange);
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
        }
    }
}
