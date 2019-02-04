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
package org.apache.camel.reifier;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

class IdempotentConsumerReifier extends ExpressionReifier<IdempotentConsumerDefinition> {

    IdempotentConsumerReifier(ProcessorDefinition<?> definition) {
        super(IdempotentConsumerDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        IdempotentRepository idempotentRepository = resolveMessageIdRepository(routeContext);
        ObjectHelper.notNull(idempotentRepository, "idempotentRepository", definition);

        Expression expression = definition.getExpression().createExpression(routeContext);

        // these boolean should be true by default
        boolean eager = definition.getEager() == null || definition.getEager();
        boolean duplicate = definition.getSkipDuplicate() == null || definition.getSkipDuplicate();
        boolean remove = definition.getRemoveOnFailure() == null || definition.getRemoveOnFailure();

        // these boolean should be false by default
        boolean completionEager = definition.getCompletionEager() != null && definition.getCompletionEager();

        return new IdempotentConsumer(expression, idempotentRepository, eager, completionEager, duplicate, remove, childProcessor);
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use
     *
     * @param routeContext route context
     * @return the repository
     */
    protected <T> IdempotentRepository resolveMessageIdRepository(RouteContext routeContext) {
        if (definition.getMessageIdRepositoryRef() != null) {
            definition.setMessageIdRepository(routeContext.mandatoryLookup(definition.getMessageIdRepositoryRef(), IdempotentRepository.class));
        }
        return definition.getMessageIdRepository();
    }
}
