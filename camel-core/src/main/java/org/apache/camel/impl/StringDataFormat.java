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
package org.apache.camel.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.util.ExchangeHelper;

/**
 * The text based <a href="http://camel.apache.org/data-format.html">data format</a> supporting
 * charset encoding.
 *
 * @version 
 */
public class StringDataFormat extends org.apache.camel.support.ServiceSupport implements DataFormat, DataFormatName {

    private String charset;

    public StringDataFormat() {
    }

    public StringDataFormat(String charset) {
        this.charset = charset;
    }

    @Override
    public String getDataFormatName() {
        return "string";
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws IOException {
        String text = ExchangeHelper.convertToType(exchange, String.class, graph);

        byte[] bytes;
        if (charset != null) {
            bytes = text.getBytes(charset);
        } else {
            bytes = text.getBytes();
        }
        stream.write(bytes);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        byte[] bytes = ExchangeHelper.convertToType(exchange, byte[].class, stream);

        String answer;
        if (charset != null) {
            answer = new String(bytes, charset);
        } else {
            answer = new String(bytes);
        }

        return answer;
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