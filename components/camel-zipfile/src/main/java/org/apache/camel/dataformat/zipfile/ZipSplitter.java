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
package org.apache.camel.dataformat.zipfile;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;

/**
 * ZipSplitter the expression builder which can be used after the splitter
 * Based on the thread <a href=
 * "http://camel.465427.n5.nabble.com/zip-file-best-practices-td5713437.html"
 * >zip file best practices</a>
 */
public class ZipSplitter implements Expression {

    public ZipSplitter() {
    }

    public Object evaluate(Exchange exchange) {
        Message inputMessage = exchange.getIn();
        InputStream inputStream = inputMessage.getBody(InputStream.class);
        return new ZipIterator(exchange, inputStream);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
    }
}
