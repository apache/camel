package org.apache.camel.processor.interceptor;

import java.util.List;

import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * An interceptor strategy for delaying routes.
 */
public class Delayer implements InterceptStrategy {

    private boolean enabled = true;
    private long delay;

    public Delayer() {
    }

    public Delayer(long delay) {
        this.delay = delay;
    }

    /**
     * A helper method to return the Delayer instance for a given {@link org.apache.camel.CamelContext} if one is enabled
     *
     * @param context the camel context the delayer is connected to
     * @return the delayer or null if none can be found
     */
    public static DelayInterceptor getDelayer(CamelContext context) {
        if (context instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) context;
            List<InterceptStrategy> list = defaultCamelContext.getInterceptStrategies();
            for (InterceptStrategy interceptStrategy : list) {
                if (interceptStrategy instanceof DelayInterceptor) {
                    return (DelayInterceptor)interceptStrategy;
                }
            }
        }
        return null;
    }

    public Processor wrapProcessorInInterceptors(ProcessorType processorType, Processor target)
        throws Exception {
        DelayInterceptor delayer = new DelayInterceptor(processorType, target, this);
        return delayer;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }
}
