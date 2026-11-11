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
package org.apache.camel.support;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.spi.StepHook;

/**
 * A {@link StepHook} that asks several hooks, in their {@link Ordered order} (lower first; a hook that does not
 * implement {@link Ordered} has order 0, and hooks with the same order keep the order they were given in).
 * <p/>
 * The hooks are asked one after the other: a hook that throws an exception, or sets one on the exchange, fails the
 * step, and the hooks after it are not asked. A hook that returns a reserved negative value fails the step as well. The
 * delays of the hooks add up: the step waits for their sum, once, after all the hooks were asked.
 * <p/>
 * It is immutable: the {@link org.apache.camel.CamelContext} creates a new one when a hook is added or removed (see
 * {@link org.apache.camel.ExtendedCamelContext#addStepHook(StepHook)}).
 *
 * @since 4.24
 */
public final class CompositeStepHook implements StepHook {

    private final StepHook[] hooks;

    private CompositeStepHook(StepHook[] hooks) {
        this.hooks = hooks;
    }

    /**
     * The hook to install for the given hooks: {@code null} for none, the hook itself for one, or else a composite that
     * asks them in their order.
     *
     * @param  hooks the hooks, in the order they were added
     * @return       the hook, or {@code null}
     */
    public static StepHook of(Collection<? extends StepHook> hooks) {
        if (hooks.isEmpty()) {
            return null;
        }
        StepHook[] sorted = hooks.toArray(new StepHook[0]);
        if (sorted.length == 1) {
            return sorted[0];
        }
        // a stable sort, so hooks with the same order keep the order they were added
        Arrays.sort(sorted, OrderedComparator.get());
        return new CompositeStepHook(sorted);
    }

    /**
     * The hooks, in the order they are asked.
     */
    public List<StepHook> getHooks() {
        return List.of(hooks);
    }

    @Override
    public long beforeStep(Exchange exchange, String nodeId) {
        // an exchange can reach a step with an exception on it already, which does not fail the step
        final Exception existing = exchange.getException();
        long total = PROCEED;
        for (StepHook hook : hooks) {
            // a hook that throws fails the step (the caller sets the exception), and the later hooks are not asked
            long delay = hook.beforeStep(exchange, nodeId);
            Exception exception = exchange.getException();
            if (exception != null && exception != existing) {
                // the hook failed the step: the return value is ignored
                return PROCEED;
            }
            if (delay < 0) {
                // reserved: the caller fails the step
                return delay;
            }
            total = delay > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + delay;
        }
        return total;
    }

    @Override
    public String toString() {
        return "CompositeStepHook" + Arrays.toString(hooks);
    }
}
