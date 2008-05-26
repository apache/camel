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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implements try/catch/finally type processing
 *
 * @version $Revision$
 */
public class TryProcessor extends ServiceSupport implements Processor {
    private static final transient Log LOG = LogFactory.getLog(TryProcessor.class);

    private final Processor tryProcessor;
    private final List<CatchProcessor> catchClauses;
    private final Processor finallyProcessor;

    public TryProcessor(Processor tryProcessor, List<CatchProcessor> catchClauses, Processor finallyProcessor) {
        this.tryProcessor = tryProcessor;
        this.catchClauses = catchClauses;
        this.finallyProcessor = finallyProcessor;
    }

    public String toString() {
        String finallyText = (finallyProcessor == null) ? "" : " Finally {" + finallyProcessor + "}";
        return "Try {" + tryProcessor + "} " + catchClauses + finallyText;
    }

    public void process(Exchange exchange) throws Exception {
        Throwable e = null;
        try {
            tryProcessor.process(exchange);
            e = exchange.getException();

            // Ignore it if it was handled by the dead letter channel.
            if (e != null && DeadLetterChannel.isFailureHandled(exchange)) {
                e = null;
            }
        } catch (Throwable ex) {
            e = ex;
            exchange.setException(e);
        }

        Exception unexpected = null;
        try {
            if (e != null) {
                LOG.info("Caught exception while processing exchange.", e);
                handleException(exchange, e);
            }
        } catch (Exception ex) {
            unexpected = ex;
        } catch (Throwable ex) {
            unexpected = new RuntimeCamelException(ex);
        } finally {
            try {
                processFinally(exchange);
            } catch (Exception ex) {
                unexpected = ex;
            } catch (Throwable ex) {
                unexpected = new RuntimeCamelException(ex);
            }
            if (unexpected != null) {
                LOG.warn("Caught exception inside processFinally clause.", unexpected);
                throw unexpected;
            }
        }

        if (unexpected != null) {
            LOG.warn("Caught exception inside handle clause.", unexpected);
            throw unexpected;
        }
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(tryProcessor, catchClauses, finallyProcessor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(tryProcessor, catchClauses, finallyProcessor);
    }

    protected void handleException(Exchange exchange, Throwable e) throws Throwable {
        for (CatchProcessor catchClause : catchClauses) {
            if (catchClause.catches(e)) {
                // lets attach the exception to the exchange
                Exchange localExchange = exchange.copy();
                localExchange.getIn().setHeader("caught.exception", e);
                // give the rest of the pipeline another chance
                localExchange.setException(null);

                // do not catch any exception here, let it propagate up
                catchClause.process(localExchange);
                localExchange.getIn().removeHeader("caught.exception");
                ExchangeHelper.copyResults(exchange, localExchange);
                return;
            }
        }
    }

    protected void processFinally(Exchange exchange) throws Throwable {
        if (finallyProcessor != null) {
            Throwable lastException = exchange.getException();
            exchange.setException(null);

            // do not catch any exception here, let it propagate up
            finallyProcessor.process(exchange);
            if (exchange.getException() == null) {
                exchange.setException(lastException);
            }
        }
    }
}
