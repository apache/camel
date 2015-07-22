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
package org.apache.camel.component.interactivebrokers;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.controller.ApiController.IOrderHandler;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractiveBrokersProducer extends DefaultProducer {

    private final Logger logger = LoggerFactory.getLogger(InteractiveBrokersProducer.class);

    private InteractiveBrokersBinding binding;

    public InteractiveBrokersProducer(Endpoint endpoint, InteractiveBrokersBinding binding) {
        super(endpoint);
        this.binding = binding;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // can't do anything if we are not connected yet
        binding.ensureConnected();
        
        Message in = exchange.getIn();
        Contract contract = in.getHeader(InteractiveBrokersConstants.CONTRACT, Contract.class);
        Order order = in.getBody(Order.class);

        IOrderHandlerImpl handler = new IOrderHandlerImpl();
        logger.info("Placing order to {} symbol {}", order.action(), contract.symbol());
        binding.getApiController().placeOrModifyOrder(contract, order, handler);

        OrderState status = handler.getOrderState();

        Message out = in.copy();
        out.setBody(status);
        exchange.setOut(out);
    }

    class IOrderHandlerImpl implements IOrderHandler {

        private volatile OrderState orderState;

        public OrderState getOrderState() {
            while (orderState == null) {
                Thread.yield();
            }
            return orderState;
        }

        @Override
        public void orderState(OrderState orderState) {
            binding.getApiController().removeOrderHandler(this);
            logger.info("Got order acknowledgement");
            this.orderState = orderState;
        }

        @Override
        public void orderStatus(OrderStatus status, int filled, int remaining, double avgFillPrice, long permId,
                int parentId, double lastFillPrice, int clientId, String whyHeld) {
            // TODO Auto-generated method stub
        }

        @Override
        public void handle(int errorCode, String errorMsg) {
            // TODO Auto-generated method stub
            logger.error("TWS API IOrderHandler error, code = {}, msg = {}", errorCode, errorMsg);
        }
    }
}