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
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which converts the payload of the input message to be of the given type
 * <p/>
 * If the conversion fails an {@link org.apache.camel.InvalidPayloadException} is thrown.
 *
 * @version 
 */
public class ConvertBodyProcessor extends ServiceSupport implements Processor {
    private final Class<?> type;
    private final String charset;

    public ConvertBodyProcessor(Class<?> type) {
        ObjectHelper.notNull(type, "type", this);
        this.type = type;
        this.charset = null;
    }

    public ConvertBodyProcessor(Class<?> type, String charset) {
        ObjectHelper.notNull(type, "type", this);
        this.type = type;
        this.charset = IOHelper.normalizeCharset(charset);
    }

    @Override
    public String toString() {
        return "convertBodyTo[" + type.getCanonicalName() + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            // only convert if the is a body
            return;
        }

        if (charset != null) {
            // override existing charset with configured charset as that is what the user
            // have explicit configured and expects to be used
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
        }
        // use mandatory conversion
        Object value = in.getMandatoryBody(type);

        // create a new message container so we do not drag specialized message objects along
        Message msg = new DefaultMessage();
        msg.copyFrom(in);
        msg.setBody(value);

        if (exchange.getPattern().isOutCapable()) {
            exchange.setOut(msg);
        } else {
            exchange.setIn(msg);
        }

        // remove charset when we are done as we should not propagate that,
        // as that can lead to double converting later on
        if (charset != null) {
            exchange.removeProperty(Exchange.CHARSET_NAME);
        }
    }

    public Class<?> getType() {
        return type;
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
