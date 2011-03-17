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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;

/**
 * Processor for marking an {@link org.apache.camel.Exchange} to rollback.
 *
 * @version 
 */
public class RollbackProcessor implements Processor, Traceable {

    private boolean markRollbackOnly;
    private boolean markRollbackOnlyLast;
    private String message;

    public RollbackProcessor() {
    }

    public RollbackProcessor(String message) {
        this.message = message;
    }

    public void process(Exchange exchange) throws Exception {
        if (isMarkRollbackOnlyLast()) {
            // only mark the last route (current) as rollback
            // this is needed when you have multiple transactions in play
            exchange.setProperty(Exchange.ROLLBACK_ONLY_LAST, Boolean.TRUE);
        } else {
            // default to mark the entire route as rollback
            exchange.setProperty(Exchange.ROLLBACK_ONLY, Boolean.TRUE);
        }

        if (markRollbackOnly || markRollbackOnlyLast) {
            // do not do anything more as we should only mark the rollback
            return;
        }

        if (message != null) {
            exchange.setException(new RollbackExchangeException(message, exchange));
        } else {
            exchange.setException(new RollbackExchangeException(exchange));
        }
    }

    @Override
    public String toString() {
        if (message != null) {
            return "Rollback[" + message + "]";
        } else {
            return "Rollback";
        }
    }

    public String getTraceLabel() {
        return "rollback";
    }

    public boolean isMarkRollbackOnly() {
        return markRollbackOnly;
    }

    public void setMarkRollbackOnly(boolean markRollbackOnly) {
        this.markRollbackOnly = markRollbackOnly;
    }

    public boolean isMarkRollbackOnlyLast() {
        return markRollbackOnlyLast;
    }

    public void setMarkRollbackOnlyLast(boolean markRollbackOnlyLast) {
        this.markRollbackOnlyLast = markRollbackOnlyLast;
    }
}
