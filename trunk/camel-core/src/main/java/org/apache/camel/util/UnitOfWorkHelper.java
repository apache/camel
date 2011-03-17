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
package org.apache.camel.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;

/**
 * @version 
 */
public final class UnitOfWorkHelper {

    private UnitOfWorkHelper() {
    }

    @SuppressWarnings("unchecked")
    public static void doneSynchronizations(Exchange exchange, List<Synchronization> synchronizations, Logger log) {
        boolean failed = exchange.isFailed();

        if (synchronizations != null && !synchronizations.isEmpty()) {
            // work on a copy of the list to avoid any modification which may cause ConcurrentModificationException
            List<Synchronization> copy = new ArrayList<Synchronization>(synchronizations);

            // reverse so we invoke it FILO style instead of FIFO
            Collections.reverse(copy);
            // and honor if any was ordered by sorting it accordingly
            Collections.sort(copy, new OrderedComparator());

            // invoke synchronization callbacks
            for (Synchronization synchronization : copy) {
                try {
                    if (failed) {
                        if (log.isTraceEnabled()) {
                            log.trace("Invoking synchronization.onFailure: " + synchronization + " with " + exchange);
                        }
                        synchronization.onFailure(exchange);
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Invoking synchronization.onComplete: " + synchronization + " with " + exchange);
                        }
                        synchronization.onComplete(exchange);
                    }
                } catch (Throwable e) {
                    // must catch exceptions to ensure all synchronizations have a chance to run
                    log.warn("Exception occurred during onCompletion. This exception will be ignored.", e);
                }
            }
        }
    }

}
