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
package org.apache.camel.dataformat.xsdjson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class XsdJsonTestSupport {

    protected CamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    protected XsdJsonDataFormat dataFormat(String schema, Consumer<XsdJsonDataFormat> config) {
        XsdJsonDataFormat df = new XsdJsonDataFormat();
        df.setCamelContext(context);
        df.setSchema(schema);
        config.accept(df);
        df.start();
        return df;
    }

    protected Exchange exchange() {
        return new DefaultExchange(context);
    }

    protected String toJson(XsdJsonDataFormat df, String xml) throws Exception {
        return toJson(df, exchange(), xml);
    }

    protected String toJson(XsdJsonDataFormat df, Exchange exchange, String xml) throws Exception {
        return (String) df.unmarshal(exchange, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    protected String toXml(XsdJsonDataFormat df, Object json) throws Exception {
        return toXml(df, exchange(), json);
    }

    protected String toXml(XsdJsonDataFormat df, Exchange exchange, Object json) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        df.marshal(exchange, json, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
