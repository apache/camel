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
package org.apache.camel.component.redis;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public abstract class AbstractRedisProcessorCreator implements RedisProcessorsCreator {
    protected Map<Command, Processor> result = new HashMap<>();

    @Override
    public Map<Command, Processor> getRedisProcessors() {
        return result;
    }

    protected void bind(Command command, Processor processor) {
        result.put(command, processor);
    }

    private void setResult(Exchange exchange, Object result) {
        Message message;
        if (exchange.getPattern().isOutCapable()) {
            message = exchange.getOut();
            message.copyFrom(exchange.getIn());
        } else {
            message = exchange.getIn();
        }
        message.setBody(result);
    }

    protected Processor wrap(Function<Exchange, Object> supplier) {
        return exchange -> {
            Object result = supplier.apply(exchange);
            setResult(exchange, result);
        };
    }
}
