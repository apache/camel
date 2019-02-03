package org.apache.camel.component.jcache.policy;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;

public class CachePolicyProcessor extends DelegateAsyncProcessor {
    private static final Logger log = LoggerFactory.getLogger(CachePolicyProcessor.class);


    private Cache cache;
    private Expression keyExpression;

    public CachePolicyProcessor(Cache cache, Expression keyExpression, Processor processor) {
        super(processor);
        this.cache = cache;
        this.keyExpression = keyExpression;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        log.debug("CachePolicy process started - cache:{}, exchange:{}", cache.getName(), exchange.getExchangeId());

        //If cache is closed, just continue
        if (cache.isClosed())
            return super.process(exchange,callback);


        try {
            //Get key by the expression or use message body
            Object key = keyExpression != null ? keyExpression.evaluate(exchange, Object.class) : exchange.getMessage().getBody();

            //Check if cache contains the key
            if (key != null) {
                Object value = cache.get(key);
                if (value != null) {
                    // use the cached object in the Exchange without calling the rest of the route
                    log.debug("Cached object is found, skipping the route - key:{}, exchange:{}", key, exchange.getExchangeId());

                    exchange.getMessage().setBody(value);

                    callback.done(true);
                    return true;
                }
            }


            //Not found in cache. Continue route.
            log.debug("No cached object is found, continue route - key:{}, exchange:{}", key, exchange.getExchangeId());

            return super.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    try {
                        if (!exchange.isFailed()) {
                            //Save body in cache after successfully executing the route
                            Object value = exchange.getMessage().getBody();

                            if (value != null) {
                                log.debug("Saving in cache - key:{}, value:{}, exchange:{}", key, value, exchange.getExchangeId());
                                cache.put(key, value);
                            }
                        }
                    } catch (Exception ex){
                        //Log exception, but a problem with caching should not fail the exchange
                        log.error("Error storing in cache. - key:{}, exchange:{}",key, exchange.getExchangeId(),ex);
                    } finally {
                        callback.done(doneSync);
                    }
                }
            });

        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Expression getKeyExpression() {
        return keyExpression;
    }

    public void setKeyExpression(Expression keyExpression) {
        this.keyExpression = keyExpression;
    }
}
