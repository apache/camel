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
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implements try/catch/finally type processing
 * 
 * @version $Revision: $
 */
public class TryProcessor extends ServiceSupport implements Processor {
    private static final Log LOG = LogFactory.getLog(TryProcessor.class);

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
        } catch (Exception ex) {
            e = ex;
            exchange.setException(e);
        }

        if (e != null) {
            try {
                DeadLetterChannel.setFailureHandled(exchange, true);
                handleException(exchange, e);
            } catch (Exception ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new RuntimeCamelException(ex);
            } finally {
                handleAll(exchange);
            }
        } else {
            handleAll(exchange);
        }

    }

    private void handleAll(Exchange exchange) {
        if (finallyProcessor != null) {
            DeadLetterChannel.setFailureHandled(exchange, true);
            try {
                finallyProcessor.process(exchange);
            } catch (Exception e2) {
                LOG.warn("Caught exception in finally block while handling other exception: " + e2, e2);
            }
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
                exchange.setException(e);
                try {
                    catchClause.process(exchange);
                } catch (Exception e1) {
                    LOG.warn("Caught exception inside catch clause: " + e1, e1);
                    throw e1;
                }
                return;
            }
        }

        // unhandled exception
        if (finallyProcessor == null) {
            throw e;
        }
    }
}
