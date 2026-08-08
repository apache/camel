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
package org.apache.camel.component.rest.postman;

import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.spi.CamelInternalProcessorAdvice;

/**
 * Runs the Postman request dispatcher after the rest of the internal processing, replacing the stock REST binding
 * advice, which is not used because binding is decided per request rather than per route.
 */
class RestPostmanProcessorAdvice implements CamelInternalProcessorAdvice<Object>, Ordered {

    private final RestPostmanProcessor postmanProcessor;

    RestPostmanProcessorAdvice(RestPostmanProcessor postmanProcessor) {
        this.postmanProcessor = postmanProcessor;
    }

    @Override
    public boolean hasState() {
        return false;
    }

    @Override
    public Object before(Exchange exchange) throws Exception {
        try {
            postmanProcessor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        return null;
    }

    @Override
    public void after(Exchange exchange, Object data) throws Exception {
        // noop
    }

    @Override
    public int getOrder() {
        // lowest so that all existing advices are triggered first
        return Ordered.LOWEST;
    }
}
