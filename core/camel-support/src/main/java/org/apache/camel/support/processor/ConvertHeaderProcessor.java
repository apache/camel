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
package org.apache.camel.support.processor;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderOrPropertyException;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A processor which converts the message header to be of the given type
 */
public class ConvertHeaderProcessor extends ServiceSupport implements AsyncProcessor, IdAware, RouteIdAware {
    private String id;
    private String routeId;
    private final String name;
    private final Expression headerName;
    private final String toName;
    private final Expression toHeaderName;
    private final Class<?> type;
    private final String charset;
    private final boolean mandatory;

    public ConvertHeaderProcessor(String name, Expression headerName, String toName, Expression toHeaderName,
                                  Class<?> type, String charset, boolean mandatory) {
        ObjectHelper.notNull(headerName, "headerName");
        ObjectHelper.notNull(type, "type", this);
        this.name = name;
        this.headerName = headerName;
        this.toName = toName;
        this.toHeaderName = toHeaderName;
        this.type = type;
        this.charset = IOHelper.normalizeCharset(charset);
        this.mandatory = mandatory;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message old = exchange.getMessage();

        // what is the header name
        String name = headerName.evaluate(exchange, String.class);
        String targetName = toHeaderName != null ? toHeaderName.evaluate(exchange, String.class) : name;

        if (old.getHeader(name) == null) {
            // only convert if there is a header
            return;
        }

        if (exchange.getException() != null) {
            // do not convert if an exception has been thrown as if we attempt to convert and it also fails with a new
            // exception then it will override the existing exception
            return;
        }

        String originalCharsetName = null;
        if (charset != null) {
            originalCharsetName = exchange.getProperty(ExchangePropertyKey.CHARSET_NAME, String.class);
            // override existing charset with configured charset as that is what the user
            // have explicit configured and expects to be used
            exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, charset);
        }
        // use mandatory conversion
        Object value = old.getHeader(name);
        if (value == null && mandatory) {
            throw new NoSuchHeaderOrPropertyException(exchange, name, null, type);
        }
        if (mandatory) {
            value = exchange.getContext().getTypeConverter().mandatoryConvertTo(type, exchange, value);
        } else {
            value = exchange.getContext().getTypeConverter().convertTo(type, exchange, value);
        }
        old.setHeader(targetName, value);

        // remove or restore charset when we are done as we should not propagate that,
        // as that can lead to double converting later on
        if (charset != null) {
            if (originalCharsetName != null && !originalCharsetName.isEmpty()) {
                exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, originalCharsetName);
            } else {
                exchange.removeProperty(ExchangePropertyKey.CHARSET_NAME);
            }
        }
    }

    @Override
    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
        AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
        process(exchange, callback);
        return callback.getFuture();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    public String getName() {
        return name;
    }

    public String getToName() {
        return toName;
    }

    public Class<?> getType() {
        return type;
    }

    public String getCharset() {
        return charset;
    }
}
