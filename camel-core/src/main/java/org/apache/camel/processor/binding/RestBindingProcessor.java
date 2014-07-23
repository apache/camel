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

    public RestBindingProcessor(DataFormat jsonDataFormat, DataFormat xmlDataFormat) {
        this.jsonUnmarshal = jsonDataFormat != null ? new UnmarshalProcessor(jsonDataFormat) : null;
        this.jsonMmarshal = jsonDataFormat != null ? new MarshalProcessor(jsonDataFormat) : null;
        this.xmlUnmarshal = xmlDataFormat != null ? new UnmarshalProcessor(xmlDataFormat) : null;
        this.xmlMmarshal = xmlDataFormat != null ? new MarshalProcessor(xmlDataFormat) : null;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        // is there any unmarshaller at all
        if (jsonUnmarshal == null && xmlUnmarshal == null) {
            callback.done(true);
            return true;
        }

        // check the header first if its xml or json
        String contentType = ExchangeHelper.getContentType(exchange);
        boolean isXml = contentType != null && contentType.toLowerCase(Locale.US).contains("xml");
        boolean isJson = contentType != null && contentType.toLowerCase(Locale.US).contains("json");
        if (isXml && xmlUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal, true));
            return xmlUnmarshal.process(exchange, callback);
        } else if (isJson && jsonUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal, false));
            return jsonUnmarshal.process(exchange, callback);
        }

        // read the content into memory so we can determine if its xml or json
        String body = MessageHelper.extractBodyAsString(exchange.getIn());
        isXml = body.startsWith("<") || body.contains("xml");

        if (isXml && xmlUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal, true));
            return xmlUnmarshal.process(exchange, callback);
        } else if (jsonUnmarshal != null) {
            // add reverse operation
            exchange.addOnCompletion(new RestBindingMarshalOnCompletion(jsonMmarshal, xmlMmarshal, false));
            return jsonUnmarshal.process(exchange, callback);
        } else {
            // noop
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
        private final boolean wasXml;

        private RestBindingMarshalOnCompletion(AsyncProcessor jsonMmarshal, AsyncProcessor xmlMmarshal, boolean wasXml) {
            this.jsonMmarshal = jsonMmarshal;
            this.xmlMmarshal = xmlMmarshal;
            this.wasXml = wasXml;
        }

        @Override
        public void onComplete(Exchange exchange) {
            // only marshal if we succeeded

            // need to prepare exchange first
            ExchangeHelper.prepareOutToIn(exchange);

            // TODO: add logic to detect what content-type is now
            // also when we add support for @Produces then use that to determine if we should marshal to xml or json
            try {
                if (wasXml) {
                    xmlMmarshal.process(exchange);
                } else {
                    jsonMmarshal.process(exchange);
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
        }
    }
}
