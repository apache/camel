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

import java.time.Duration;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CacheDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CacheProcessor;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheReifier extends ExpressionReifier<CacheDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(CacheReifier.class);

    public CacheReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, CacheDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor childProcessor = this.createChildProcessor(true);

        KeyValueRepository kvRepository = resolveKeyValueRepository();
        ObjectHelper.notNull(kvRepository, "keyValueRepository", definition);

        Expression expression = createExpression(definition.getExpression());

        long ttlMillis = parseDuration(definition.getTtl(), -1);
        Duration ttl = ttlMillis > 0 ? Duration.ofMillis(ttlMillis) : null;
        boolean cacheNull = parseBoolean(definition.getCacheNull(), false);

        CacheProcessor answer = new CacheProcessor(expression, kvRepository, ttl, cacheNull, childProcessor);
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }

    /**
     * Strategy method to resolve the {@link KeyValueRepository} to use.
     * <p/>
     * Resolution order:
     * <ol>
     * <li>Explicit bean set programmatically</li>
     * <li>Registry lookup by reference name</li>
     * <li>Auto-discover a single KeyValueRepository from the registry</li>
     * <li>Auto-create a MemoryKeyValueRepository</li>
     * </ol>
     */
    protected KeyValueRepository resolveKeyValueRepository() {
        KeyValueRepository repo = definition.getKeyValueRepositoryBean();
        String ref = parseString(definition.getKeyValueRepository());
        if (repo == null && ref != null) {
            repo = mandatoryLookup(ref, KeyValueRepository.class);
        }
        if (repo == null) {
            // Auto-discover a KeyValueRepository from the registry
            repo = camelContext.getRegistry().findSingleByType(KeyValueRepository.class);
            if (repo != null) {
                LOG.info("Auto-discovered KeyValueRepository from registry for Cache EIP");
            }
        }
        if (repo == null) {
            // Auto-create an in-memory repository as the default
            LOG.info("No KeyValueRepository found, auto-creating MemoryKeyValueRepository for Cache EIP");
            repo = new MemoryKeyValueRepository();
        }
        return repo;
    }

}
