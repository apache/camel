/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Expression;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MessageIdRepository;

/**
 * A builder of an {@link IdempotentConsumer}
 *
 * @version $Revision: 1.1 $
 */
public class IdempotentConsumerBuilder extends FromBuilder implements ProcessorFactory {
    private final Expression messageIdExpression;
    private final MessageIdRepository messageIdRegistry;

    public IdempotentConsumerBuilder(FromBuilder fromBuilder, Expression messageIdExpression, MessageIdRepository messageIdRegistry) {
        super(fromBuilder);
        this.messageIdRegistry = messageIdRegistry;
        this.messageIdExpression = messageIdExpression;
    }

    // Properties
    //-------------------------------------------------------------------------
    public MessageIdRepository getMessageIdRegistry() {
        return messageIdRegistry;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    @Override
    protected Processor wrapInErrorHandler(Processor processor) throws Exception {
        // lets do no wrapping in error handlers as the parent FromBuilder will do that
        return processor;
    }

    @Override
    protected Processor wrapProcessor(Processor processor) {
        return new IdempotentConsumer(messageIdExpression, messageIdRegistry, processor);
    }
}
