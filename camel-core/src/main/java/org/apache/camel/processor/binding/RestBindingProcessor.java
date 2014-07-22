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
import org.apache.camel.processor.UnmarshalProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;

public class RestBindingProcessor extends ServiceSupport implements AsyncProcessor {

    private final AsyncProcessor jsonUnmarshal;
    private final AsyncProcessor xmlUnmarshal;

    public RestBindingProcessor(DataFormat jsonDataFormat, DataFormat xmlDataFormat) {
        this.jsonUnmarshal = jsonDataFormat != null ? new UnmarshalProcessor(jsonDataFormat) : null;
        this.xmlUnmarshal = xmlDataFormat != null ? new UnmarshalProcessor(xmlDataFormat) : null;
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
            return xmlUnmarshal.process(exchange, callback);
        } else if (isJson && jsonUnmarshal != null) {
            return jsonUnmarshal.process(exchange, callback);
        }

        // read the content into memory so we can determine if its xml or json
        String body = MessageHelper.extractBodyAsString(exchange.getIn());
        isXml = body.startsWith("<") || body.contains("xml");

        if (isXml && xmlUnmarshal != null) {
            return xmlUnmarshal.process(exchange, callback);
        } else if (jsonUnmarshal != null) {
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
}
