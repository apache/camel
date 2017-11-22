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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which converts the payload of the input message to be of the given type
 * <p/>
 * If the conversion fails an {@link org.apache.camel.InvalidPayloadException} is thrown.
 *
 * @version 
 */
public class ConvertBodyProcessor extends ServiceSupport implements AsyncProcessor, IdAware {
    private String id;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        boolean out = exchange.hasOut();
        Message old = out ? exchange.getOut() : exchange.getIn();

        if (old.getBody() == null) {
            // only convert if the is a body
            callback.done(true);
            return true;
        }

        if (exchange.getException() != null) {
            // do not convert if an exception has been thrown as if we attempt to convert and it also fails with a new
            // exception then it will override the existing exception
            callback.done(true);
            return true;
        }

        if (charset != null) {
            // override existing charset with configured charset as that is what the user
            // have explicit configured and expects to be used
            exchange.setProperty(Exchange.CHARSET_NAME, charset);
        }
        // use mandatory conversion
        Object value;
        try {
            value = old.getMandatoryBody(type);
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // create a new message container so we do not drag specialized message objects along
        // but that is only needed if the old message is a specialized message
        boolean copyNeeded = !(old.getClass().equals(DefaultMessage.class));

        if (copyNeeded) {
            Message msg = new DefaultMessage(exchange.getContext());
            msg.copyFromWithNewBody(old, value);

            // replace message on exchange
            ExchangeHelper.replaceMessage(exchange, msg, false);
        } else {
            // no copy needed so set replace value directly
            old.setBody(value);
        }

        // remove charset when we are done as we should not propagate that,
        // as that can lead to double converting later on
        if (charset != null) {
            exchange.removeProperty(Exchange.CHARSET_NAME);
        }

        callback.done(true);
        return true;
    }

    public Class<?> getType() {
        return type;
    }

    public String getCharset() {
        return charset;
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
