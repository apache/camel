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
package org.apache.camel.component.cedar;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

final class CedarSecurityProcessor extends DelegateAsyncProcessor {
    private CedarEvaluator evaluator;
    private CedarSecurityPolicy policy;
    private CamelContext context;
    private boolean acquired;

    CedarSecurityProcessor(Processor processor, CedarEvaluator evaluator) {
        super(processor);
        this.evaluator = evaluator;
    }

    CedarSecurityProcessor(Processor processor, CedarSecurityPolicy policy, CamelContext context) {
        super(processor);
        this.policy = policy;
        this.context = context;
    }

    @Override
    protected void doStart() throws Exception {
        if (policy != null) {
            evaluator = policy.acquire(context);
        } else {
            evaluator.start();
        }
        acquired = true;
        try {
            super.doStart();
        } catch (Exception e) {
            release();
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        try {
            super.doStop();
        } finally {
            release();
        }
    }

    private void release() throws Exception {
        if (!acquired) {
            return;
        }
        acquired = false;
        if (policy != null) {
            policy.release(context);
            evaluator = null;
        } else {
            evaluator.stop();
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.getMessage().removeHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID);
        CedarAuthorizationResult result = evaluator.evaluate(exchange);
        if (!result.isAllowed()) {
            exchange.getMessage().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, "CedarSecurityPolicy");
            exchange.setException(new CamelAuthorizationException("Cedar authorization " + result.outcome(), exchange));
            callback.done(true);
            return true;
        }
        return super.process(exchange, callback);
    }
}
