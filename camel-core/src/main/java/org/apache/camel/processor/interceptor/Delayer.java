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
package org.apache.camel.processor.interceptor;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 * An interceptor strategy for delaying routes.
 *
 * @deprecated no longer in use, will be removed in next Camel release.
 */
@Deprecated
public class Delayer implements InterceptStrategy {

    private volatile boolean enabled = true;
    private final long delay;

    public Delayer(long delay) {
        this.delay = delay;
    }

    /**
     * A helper method to return the Delayer instance for a given {@link org.apache.camel.CamelContext} if one is enabled
     *
     * @param context the camel context the delayer is connected to
     * @return the delayer or null if none can be found
     */
    public static Delayer getDelayer(CamelContext context) {
        List<InterceptStrategy> list = context.getInterceptStrategies();
        for (InterceptStrategy interceptStrategy : list) {
            if (interceptStrategy instanceof Delayer) {
                return (Delayer)interceptStrategy;
            }
        }
        return null;
    }

    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition,
                                                 Processor target, Processor nextTarget) throws Exception {
        return new DelayInterceptor(context, definition, target, this);
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

    @Override
    public String toString() {
        return "Delayer[" + delay + "]";
    }
}
