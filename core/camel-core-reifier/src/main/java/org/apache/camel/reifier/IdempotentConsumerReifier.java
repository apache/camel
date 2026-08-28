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
package org.apache.camel.reifier;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdempotentConsumerReifier extends ExpressionReifier<IdempotentConsumerDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotentConsumerReifier.class);

    public IdempotentConsumerReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, IdempotentConsumerDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor childProcessor = this.createChildProcessor(true);

        IdempotentRepository idempotentRepository = resolveIdempotentRepository();
        ObjectHelper.notNull(idempotentRepository, "idempotentRepository", definition);

        Expression expression = createExpression(definition.getExpression());

        // these boolean should be true by default
        boolean eager = parseBoolean(definition.getEager(), true);
        boolean duplicate = parseBoolean(definition.getSkipDuplicate(), true);
        boolean remove = parseBoolean(definition.getRemoveOnFailure(), true);
        // these boolean should be false by default
        boolean completionEager = parseBoolean(definition.getCompletionEager(), false);

        IdempotentConsumer answer = new IdempotentConsumer(
                expression, idempotentRepository, eager, completionEager, duplicate, remove, childProcessor);
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }

    /**
     * Strategy method to resolve the {@link org.apache.camel.spi.IdempotentRepository} to use.
     * <p/>
     * If no explicit {@link IdempotentRepository} is configured (neither as a bean reference nor inline), this method
     * falls back to looking up a {@link KeyValueRepository} from the Camel registry and wrapping it in a
     * {@link KeyValueIdempotentRepository} adapter.
     *
     * @return the repository, or {@code null} if none could be resolved
     */
    protected <T> IdempotentRepository resolveIdempotentRepository() {
        IdempotentRepository repo = definition.getIdempotentRepositoryBean();
        String ref = parseString(definition.getIdempotentRepository());
        if (repo == null && ref != null) {
            repo = mandatoryLookup(ref, IdempotentRepository.class);
        }
        if (repo == null) {
            // Fallback: auto-discover a KeyValueRepository from the registry
            KeyValueRepository kvRepo = camelContext.getRegistry().findSingleByType(KeyValueRepository.class);
            if (kvRepo != null) {
                LOG.info("Auto-discovered KeyValueRepository from registry, wrapping as IdempotentRepository");
                repo = new KeyValueIdempotentRepository(kvRepo);
            }
        }
        return repo;
    }
}
