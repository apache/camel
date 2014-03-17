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
package org.apache.camel.component.infinispan;

import java.util.List;

import org.apache.camel.Exchange;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.query.SearchManager;
import org.infinispan.query.dsl.QueryBuilder;
import org.infinispan.query.dsl.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanOperation {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanOperation.class);
    private final BasicCache<Object, Object> cache;
    private final InfinispanConfiguration configuration;

    public InfinispanOperation(BasicCache<Object, Object> cache, InfinispanConfiguration configuration) {
        this.cache = cache;
        this.configuration = configuration;
    }

    public void process(Exchange exchange) {
        Operation operation = getOperation(exchange);
        operation.setBasicCache(cache);
        operation.setConfiguration(configuration);
        operation.execute(exchange);
    }

    private Operation getOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(InfinispanConstants.OPERATION, String.class);
        if (operation == null) {
            operation = InfinispanConstants.PUT;
        }
        LOGGER.trace("Operation: [{}]", operation);
        return Operation.valueOf(operation.substring(InfinispanConstants.OPERATION.length()).toUpperCase());
    }

    enum Operation {
        PUT {
            @Override
            void execute(Exchange exchange) {
                Object result = cache.put(getKey(exchange), getValue(exchange));
                setResult(result, exchange);
            }
        }, GET {
            @Override
            void execute(Exchange exchange) {
                Object result = cache.get(getKey(exchange));
                setResult(result, exchange);
            }
        }, REMOVE {
            @Override
            void execute(Exchange exchange) {
                Object result = cache.remove(getKey(exchange));
                setResult(result, exchange);
            }
        }, CLEAR {
            @Override
            void execute(Exchange exchange) {
                cache.clear();
            }
        }, QUERY {
            @Override
            void execute(Exchange exchange) {
                if (configuration.getQueryBuilderStrategy() == null) {
                    throw new RuntimeException("QueryBuilderStrategy is required for executing queries!");
                }

                QueryFactory factory;
                if (cache instanceof RemoteCache) {
                    factory = Search.getQueryFactory((RemoteCache) cache);
                } else {
                    SearchManager searchManager = org.infinispan.query.Search.getSearchManager((Cache) cache);
                    factory = searchManager.getQueryFactory();
                }

                QueryBuilder queryBuilder = configuration.getQueryBuilderStrategy().createQueryBuilder(factory);
                if (queryBuilder == null) {
                    throw new RuntimeException("QueryBuilder not created!");
                }
                List<?> result = queryBuilder.build().list();
                setResult(result, exchange);
            }
        };

        InfinispanConfiguration configuration;
        BasicCache cache;

        public void setConfiguration(InfinispanConfiguration configuration) {
            this.configuration = configuration;
        }

        public void setBasicCache(BasicCache cache) {
            this.cache = cache;
        }

        void setResult(Object result, Exchange exchange) {
            exchange.getIn().setHeader(InfinispanConstants.RESULT, result);
        }

        Object getKey(Exchange exchange) {
            return exchange.getIn().getHeader(InfinispanConstants.KEY);
        }

        Object getValue(Exchange exchange) {
            return exchange.getIn().getHeader(InfinispanConstants.VALUE);
        }

        abstract void execute(Exchange exchange);
    }

}
