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
package org.apache.camel.http.common;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Pre {@link Processor} used by {@link HttpSendDynamicAware}.
 */
public class HttpSendDynamicPreProcessor implements Processor {

    private final String path;
    private final String query;

    public HttpSendDynamicPreProcessor(String path, String query) {
        this.path = path;
        this.query = query;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (path != null) {
            exchange.getIn().setHeader(Exchange.HTTP_PATH, path);
        } else {
            exchange.getIn().removeHeader(Exchange.HTTP_PATH);
        }
        if (query != null) {
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, query);
        } else {
            exchange.getIn().removeHeader(Exchange.HTTP_QUERY);
        }
    }

}
