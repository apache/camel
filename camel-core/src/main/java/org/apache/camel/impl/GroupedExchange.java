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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;


/**
 * A grouped exchange that groups together other exchanges, as a holder object.
 * <p/>
 * This grouped exchange is useable for the aggregator so multiple exchanges can be grouped
 * into this single exchange and thus only one exchange is sent for further processing.
 */
public class GroupedExchange extends DefaultExchange {

    private List<Exchange> exchanges = new ArrayList<Exchange>();

    public GroupedExchange(CamelContext context) {
        super(context);
    }

    public GroupedExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }

    public GroupedExchange(Exchange parent) {
        super(parent);
    }

    public GroupedExchange(Endpoint fromEndpoint) {
        super(fromEndpoint);
    }

    public GroupedExchange(Endpoint fromEndpoint, ExchangePattern pattern) {
        super(fromEndpoint, pattern);
    }

    public List<Exchange> getExchanges() {
        return exchanges;
    }

    public void setExchanges(List<Exchange> exchanges) {
        this.exchanges = exchanges;
    }

    public void addExchange(Exchange exchange) {
        this.exchanges.add(exchange);
    }

    public int size() {
        return exchanges.size();
    }

    public Exchange get(int index) {
        return exchanges.get(index);
    }

    @Override
    public String toString() {
        return "Exchange[Grouped with: " + exchanges.size() + " exchanges]";
    }

}
