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
package org.apache.camel.component.avro;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.specific.SpecificData;

import org.apache.camel.Exchange;
import org.apache.camel.util.ExchangeHelper;

public class AvroResponder extends SpecificResponder {

    private AvroConsumer consumer;

    public AvroResponder(AvroConsumer consumer) {
        super(consumer.getEndpoint().getProtocol(), null);
        this.consumer = consumer;
    }

    @Override
    public Object respond(Protocol.Message message, Object request) throws Exception {
        Object response;
        int numParams = message.getRequest().getFields().size();
        Object[] params = new Object[numParams];
        Class<?>[] paramTypes = new Class[numParams];
        int i = 0;
        for (Schema.Field param : message.getRequest().getFields()) {
            params[i] = ((GenericRecord) request).get(param.name());
            paramTypes[i] = SpecificData.get().getClass(param.schema());
            i++;
        }
        Exchange exchange = consumer.getEndpoint().createExchange(message, params);

        try {
            consumer.getProcessor().process(exchange);
        } catch (Throwable e) {
            consumer.getExceptionHandler().handleException(e);
        }

        if (ExchangeHelper.isOutCapable(exchange)) {
            response = exchange.getOut().getBody();
        } else {
            response = null;
        }

        boolean failed = exchange.isFailed();
        if (failed) {
            if (exchange.getException() != null) {
                response = exchange.getException();
            } else {
                // failed and no exception, must be a fault
                response = exchange.getOut().getBody();
            }
        }
        return response;
    }

}
