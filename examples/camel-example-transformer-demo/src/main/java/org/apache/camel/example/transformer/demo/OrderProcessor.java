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
package org.apache.camel.example.transformer.demo;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A processor which receives a order request and return a response.
 * <p/>
 */
public class OrderProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        Order order = exchange.getIn().getBody(Order.class);
        OrderResponse answer = new OrderResponse()
            .setAccepted(true)
            .setOrderId(order.getOrderId())
            .setDescription(String.format("Order accepted:[item='%s' quantity='%s']", order.getItemId(), order.getQuantity()));
        exchange.getOut().setBody(answer);
    }
}
