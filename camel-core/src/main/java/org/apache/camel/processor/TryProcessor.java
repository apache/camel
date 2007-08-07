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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Implements try/catch/finally type processing
 *
 * @version $Revision: $
 */
public class TryProcessor implements Processor {
    private static final Log log = LogFactory.getLog(TryProcessor.class);

    private final Processor tryProcessor;
    private final List<CatchProcessor> catchClauses;
    private final Processor finallyProcessor;

    public TryProcessor(Processor tryProcessor, List<CatchProcessor> catchClauses, Processor finallyProcessor) {
        this.tryProcessor = tryProcessor;
        this.catchClauses = catchClauses;
        this.finallyProcessor = finallyProcessor;
    }

    public void process(Exchange exchange) throws Exception {
        boolean doneTry = false;
        try {
            tryProcessor.process(exchange);
            doneTry = true;

            if (finallyProcessor != null) {
                finallyProcessor.process(exchange);
            }
        }
        catch (Exception e) {
            handleException(exchange, e);
            
            if (!doneTry && finallyProcessor != null) {
                try {
                    finallyProcessor.process(exchange);
                }
                catch (Exception e2) {
                    log.warn("Caught exception in finally block while handling other exception: " + e2, e2);
                }
            }
        }
    }

    protected void handleException(Exchange exchange, Exception e) throws Exception {
        for (CatchProcessor catchClause : catchClauses) {
            if (catchClause.catches(e)) {
                try {
                    catchClause.process(exchange);
                }
                catch (Exception e1) {
                    log.warn("Caught exception inside catch clause: " + e1, e1);
                    throw e1;
                }
                break;
            }
        }
    }
}
