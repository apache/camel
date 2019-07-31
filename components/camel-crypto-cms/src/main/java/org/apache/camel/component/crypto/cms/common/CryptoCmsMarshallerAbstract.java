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
package org.apache.camel.component.crypto.cms.common;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.commons.codec.binary.Base64OutputStream;

public abstract class CryptoCmsMarshallerAbstract implements Processor {

    private final CryptoCmsMarshallerConfiguration config;

    public CryptoCmsMarshallerAbstract(CryptoCmsMarshallerConfiguration config) {
        this.config = config;
    }

    // @Override
    public CryptoCmsMarshallerConfiguration getConfiguration() {
        return config;
    }

    @Override
    public void process(Exchange exchange) throws Exception { // NOPMD all
        // exceptions must be caught to react on exception case and re-thrown,
        // see code below

        OutputStreamBuilder output = OutputStreamBuilder.withExchange(exchange);
        OutputStream outStream;
        if (config.getToBase64()) {
            outStream = new Base64OutputStream(output);
        } else {
            outStream = output;
        }

        InputStream body = exchange.getIn().getMandatoryBody(InputStream.class);

        // lets setup the out message before we invoke the processing
        // so that it can mutate it if necessary
        Message out = exchange.getOut();
        out.copyFrom(exchange.getIn());

        try {
            try {
                marshalInternal(body, outStream, exchange);
            } finally {
                IOHelper.close(outStream); // base64 stream must be closed,
                                           // before we fetch the bytes
            }

            setBodyAndHeader(out, output.build());
        } catch (Throwable e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        }
    }

    /**
     * Intended for overwriting in order to set headers and body for the out
     * message.
     */
    protected void setBodyAndHeader(Message out, Object encodedDataObject) {
        out.setBody(encodedDataObject);
    }

    protected abstract void marshalInternal(InputStream is, OutputStream os, Exchange exchange) throws Exception; // NOPMD

}
