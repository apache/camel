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
package org.apache.camel.component.spring.ws.filter.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.springframework.ws.WebServiceMessage;

/**
 * This filter is a composition of basic message filters
 */
public class CompositeMessageFilter implements MessageFilter {

    private List<MessageFilter> filters = new ArrayList<>();

    @Override
    public void filterProducer(Exchange exchange, WebServiceMessage response) {
        for (MessageFilter filter : filters) {
            filter.filterProducer(exchange, response);
        }
    }

    @Override
    public void filterConsumer(Exchange exchange, WebServiceMessage response) {
        for (MessageFilter filter : filters) {
            filter.filterConsumer(exchange, response);
        }
    }

    public void setFilters(List<MessageFilter> filters) {
        this.filters = filters;
    }

    public List<MessageFilter> getFilters() {
        return filters;
    }
}
