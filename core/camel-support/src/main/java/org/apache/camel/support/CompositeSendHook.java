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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.spi.SendHook;

/**
 * A {@link SendHook} that runs several hooks, in their {@link Ordered order} (lower first; a hook that does not
 * implement {@link Ordered} has order 0, and hooks with the same order keep the order they were given in).
 * <p/>
 * {@link SendHelper} runs the hooks of a composite one by one, as follows:
 * <ul>
 * <li>{@link #beforeSend(Exchange, Endpoint)} is called on the hooks in their order. A hook that throws an exception,
 * sets one on the exchange, returns {@link SendHook#SKIP} or a reserved negative value ends it: the hooks after it are
 * not asked. So the first {@link SendHook#SKIP} wins.</li>
 * <li>When all the hooks proceed, the longest of their delays wins: the send waits for it, once.</li>
 * <li>{@link #afterSend(Exchange, Endpoint)} is called only on the hooks whose {@code beforeSend} returned, in the
 * reverse order, so the hook that was asked first is called last, as for nested hooks.</li>
 * </ul>
 * <p/>
 * Code that calls a composite itself does not know which hooks were asked: its {@code beforeSend} combines the hooks as
 * above, and its {@code afterSend} calls every hook, in the reverse order.
 * <p/>
 * It is immutable: the {@link org.apache.camel.CamelContext} creates a new one when a hook is added or removed (see
 * {@link org.apache.camel.ExtendedCamelContext#addSendHook(SendHook)}).
 *
 * @since 4.24
 */
public final class CompositeSendHook implements SendHook {

    private final SendHook[] hooks;

    private CompositeSendHook(SendHook[] hooks) {
        this.hooks = hooks;
    }

    /**
     * The hook to install for the given hooks: {@code null} for none, the hook itself for one, or else a composite that
     * runs them in their order.
     *
     * @param  hooks the hooks, in the order they were added
     * @return       the hook, or {@code null}
     */
    public static SendHook of(Collection<? extends SendHook> hooks) {
        if (hooks.isEmpty()) {
            return null;
        }
        SendHook[] sorted = hooks.toArray(new SendHook[0]);
        if (sorted.length == 1) {
            return sorted[0];
        }
        // a stable sort, so hooks with the same order keep the order they were added
        Arrays.sort(sorted, OrderedComparator.get());
        return new CompositeSendHook(sorted);
    }

    /**
     * The hooks, in the order {@code beforeSend} is called.
     */
    public List<SendHook> getHooks() {
        return List.of(hooks);
    }

    /**
     * The hooks for {@link SendHelper}, which must not change the array.
     */
    SendHook[] hooks() {
        return hooks;
    }

    @Override
    public long beforeSend(Exchange exchange, Endpoint endpoint) {
        final Exception existing = exchange.getException();
        long answer = PROCEED;
        for (SendHook hook : hooks) {
            long value = hook.beforeSend(exchange, endpoint);
            Exception exception = exchange.getException();
            if (exception != null && exception != existing) {
                // the hook stopped the send: the return value is ignored
                return PROCEED;
            }
            if (value < 0) {
                // SKIP, or a reserved value that fails the send
                return value;
            }
            answer = Math.max(answer, value);
        }
        return answer;
    }

    @Override
    public void afterSend(Exchange exchange, Endpoint endpoint) {
        RuntimeException failure = null;
        for (int i = hooks.length - 1; i >= 0; i--) {
            try {
                hooks[i].afterSend(exchange, endpoint);
            } catch (RuntimeException e) {
                // the other hooks are still called
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public String toString() {
        return "CompositeSendHook" + Arrays.toString(hooks);
    }
}
