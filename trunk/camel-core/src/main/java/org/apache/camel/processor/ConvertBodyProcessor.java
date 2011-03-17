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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.converter.IOConverter;

/**
 * A processor which converts the payload of the input message to be of the given type
 * <p/>
 * If the convertions fails an {@link org.apache.camel.InvalidPayloadException} is thrown.
 *
 * @version 
 */
public class ConvertBodyProcessor implements Processor {
    private final Class<?> type;
    private final String charset;

    public ConvertBodyProcessor(Class<?> type) {
        this.type = type;
        this.charset = null;
    }

    public ConvertBodyProcessor(Class<?> type, String charset) {
        this.type = type;
        this.charset = charset;
    }

    @Override
    public String toString() {
        return "convertBodyTo[" + type.getCanonicalName() + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        if (charset != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOConverter.normalizeCharset(charset));
        }

        // only convert if the is a body
        if (in.getBody() != null) {
            Object value = in.getMandatoryBody(type);

            if (exchange.getPattern().isOutCapable()) {
                Message out = exchange.getOut();
                out.copyFrom(in);
                out.setBody(value);
            } else {
                in.setBody(value);
            }
        }
    }

    public Class<?> getType() {
        return type;
    }
}
