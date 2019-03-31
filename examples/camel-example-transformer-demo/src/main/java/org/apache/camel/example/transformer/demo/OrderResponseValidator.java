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

import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderResponseValidator extends Validator {

    private static final Logger LOG = LoggerFactory.getLogger(OrderResponseValidator.class);

    @Override
    public void validate(Message message, DataType type) throws ValidationException {
        Object body = message.getBody();
        LOG.info("Validating message body: {}", body);
        if (!(body instanceof OrderResponse)) {
            throw new ValidationException(message.getExchange(), "Expected OrderResponse, but was " + body.getClass());
        }
        OrderResponse r = (OrderResponse)body;
        if (!r.isAccepted()) {
            throw new ValidationException(message.getExchange(), "Order was not accepted:" + r);
        }
    }

}
