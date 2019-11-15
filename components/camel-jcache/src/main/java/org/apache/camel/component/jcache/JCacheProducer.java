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
package org.apache.camel.component.jcache;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.processor.EntryProcessor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * The JCache producer.
 */
public class JCacheProducer extends DefaultProducer {
    private final JCacheConfiguration configuration;

    public JCacheProducer(JCacheEndpoint endpoint, JCacheConfiguration configuration) {
        super(endpoint);

        this.configuration = configuration;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String actionName = exchange.getIn().getHeader(JCacheConstants.ACTION, String.class);
        if (actionName == null) {
            actionName = configuration.getAction();
        }

        StringHelper.notEmpty(actionName, JCacheConstants.ACTION);

        Action action = Action.fromName(actionName);
        if (action != null) {
            Cache<Object, Object> cache = getJCacheEndpoint().getManager().getCache();
            action.validate(cache, exchange);
            action.execute(cache, exchange);
        } else {
            throw new IllegalArgumentException(
                String.format("The value '%s' is not allowed for parameter '%s'", actionName, JCacheConstants.ACTION));
        }
    }

    @Override
    protected void doStart() throws Exception {
        getCache();

        super.doStart();
    }

    private JCacheEndpoint getJCacheEndpoint() {
        return (JCacheEndpoint)getEndpoint();
    }

    private Cache getCache() throws Exception {
        return getJCacheEndpoint().getManager().getCache();
    }

    private enum Action {
        PUT {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                cache.put(
                    exchange.getIn().getHeader(JCacheConstants.KEY),
                    exchange.getIn().getBody()
                );
            }
        },
        PUTALL {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                cache.putAll(
                    exchange.getIn().getBody(Map.class)
                );
            }
        },
        PUTIFABSENT {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setHeader(
                    JCacheConstants.RESULT,
                    cache.putIfAbsent(
                        exchange.getIn().getHeader(JCacheConstants.KEY),
                        exchange.getIn().getBody())
                );
            }
        },
        GET {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setBody(
                    cache.get(exchange.getIn().getHeader(JCacheConstants.KEY))
                );
            }
        },
        GETALL {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEYS);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setBody(
                    cache.getAll(
                        exchange.getIn().getHeader(JCacheConstants.KEYS, Set.class))
                );
            }
        },
        GETANDREMOVE {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setBody(
                    cache.getAndRemove(
                        exchange.getIn().getHeader(JCacheConstants.KEY))
                );
            }
        },
        GETANDREPLACE {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setBody(
                    cache.getAndReplace(
                        exchange.getIn().getHeader(JCacheConstants.KEY),
                        exchange.getIn().getBody())
                );
            }
        },
        GETANDPUT {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                exchange.getIn().setBody(
                    cache.getAndPut(
                        exchange.getIn().getHeader(JCacheConstants.KEY),
                        exchange.getIn().getBody())
                );
            }
        },
        REPLACE {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                Object oldValue = exchange.getIn().getHeader(JCacheConstants.OLD_VALUE);

                exchange.getIn().setHeader(
                    JCacheConstants.RESULT,
                    oldValue != null
                        ? cache.replace(
                            exchange.getIn().getHeader(JCacheConstants.KEY),
                            oldValue,
                            exchange.getIn().getBody())
                        : cache.replace(
                            exchange.getIn().getHeader(JCacheConstants.KEY),
                            exchange.getIn().getBody())
                );
            }
        },
        REMOVE {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.KEY);
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                Object oldValue = exchange.getIn().getHeader(JCacheConstants.OLD_VALUE);

                exchange.getIn().setHeader(
                    JCacheConstants.RESULT,
                    oldValue != null
                        ? cache.remove(
                            exchange.getIn().getHeader(JCacheConstants.KEY),
                            oldValue)
                        : cache.remove(
                            exchange.getIn().getHeader(JCacheConstants.KEY))
                );
            }
        },
        REMOVEALL {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                Set<Object> keys = exchange.getIn().getHeader(JCacheConstants.KEYS, Set.class);

                if (keys != null) {
                    cache.removeAll(keys);
                } else {
                    cache.removeAll();
                }
            }
        },
        INVOKE {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
                headerIsNotNull(exchange, JCacheConstants.ENTRY_PROCESSOR);

                if (exchange.getIn().getHeader(JCacheConstants.KEYS) == null
                    && exchange.getIn().getHeader(JCacheConstants.KEY) == null) {
                    throw new IllegalArgumentException(
                        String.format("Either %s or %s must be set for action %s",
                            JCacheConstants.KEYS,
                            JCacheConstants.KEY,
                            name())
                    );
                }
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                Message message = exchange.getIn();
                Set<Object> keys = message.getHeader(JCacheConstants.KEYS, Set.class);
                EntryProcessor<Object, Object, Object> entryProcessor = message.getHeader(JCacheConstants.ENTRY_PROCESSOR, EntryProcessor.class);

                Collection<Object> arguments = message.getHeader(JCacheConstants.ARGUMENTS, Collection.class);
                if (arguments == null) {
                    arguments = Collections.emptyList();
                }

                message.setBody(
                    keys != null
                        ? cache.invokeAll(
                            keys,
                            entryProcessor,
                            arguments)
                        : cache.invoke(
                            exchange.getIn().getHeader(JCacheConstants.KEY),
                            entryProcessor,
                            arguments)
                );
            }
        },
        CLEAR {
            @Override
            void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
            }

            @Override
            void execute(Cache<Object, Object> cache, Exchange exchange) {
                cache.clear();
            }
        };


        static final Action[] VALUES = values();

        static Action fromName(String name) {
            if (ObjectHelper.isNotEmpty(name)) {
                for (Action action : VALUES) {
                    if (action.name().equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }

            return null;
        }

        void validate(Cache<Object, Object> cache, Exchange exchange) throws Exception {
        }

        void execute(Cache<Object, Object> cache, Exchange exchange) {
        }

        protected void headerIsNotNull(Exchange exchange, String... keys) throws Exception {
            for (int i = keys.length - 1; i >= 0; i--) {
                if (exchange.getIn().getHeader(keys[i]) == null) {
                    throw new IllegalArgumentException(
                        String.format("Header %s must be set for action %s", keys[i], name())
                    );
                }
            }
        }
    }
}
