package org.apache.camel.component.infinispan;

import org.apache.camel.Exchange;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanOperation {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(InfinispanOperation.class);
    private BasicCache cache;

    public InfinispanOperation(BasicCache cache) {
        this.cache = cache;
    }

    public void process(Exchange exchange) {
        Operation operation = getOperation(exchange);
        operation.execute(cache, exchange);
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
            void execute(BasicCache cache, Exchange exchange) {
                Object result = cache.put(getKey(exchange), getValue(exchange));
                setResult(result, exchange);
            }
        }, GET {
            @Override
            void execute(BasicCache cache, Exchange exchange) {
                Object result = cache.get(getKey(exchange));
                setResult(result, exchange);
            }
        }, REMOVE {
            @Override
            void execute(BasicCache cache, Exchange exchange) {
                Object result = cache.remove(getKey(exchange));
                setResult(result, exchange);
            }


        }, CLEAR {
            @Override
            void execute(BasicCache cache, Exchange exchange) {
                cache.clear();
            }
        };

        void setResult(Object result, Exchange exchange) {
            exchange.getIn().setHeader(InfinispanConstants.RESULT, result);
        }

        Object getKey(Exchange exchange) {
            return exchange.getIn().getHeader(InfinispanConstants.KEY);
        }

        Object getValue(Exchange exchange) {
            return exchange.getIn().getHeader(InfinispanConstants.VALUE);
        }

        abstract void execute(BasicCache cache, Exchange exchange);
    }

}
