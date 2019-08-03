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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Traceable;
import org.apache.camel.support.AsyncProcessorSupport;

/**
 * A {@link org.apache.camel.Processor} which evaluates an {@link Expression}
 * and stores the result as a property on the {@link Exchange} with the key
 * {@link Exchange#EVALUATE_EXPRESSION_RESULT}.
 * <p/>
 * This processor will in case of evaluation exceptions, set the caused exception
 * on the {@link Exchange}.
 */
public class EvaluateExpressionProcessor extends AsyncProcessorSupport implements Traceable {

    private final Expression expression;

    public EvaluateExpressionProcessor(Expression expression) {
        this.expression = expression;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            Object result = expression.evaluate(exchange, Object.class);
            exchange.setProperty(Exchange.EVALUATE_EXPRESSION_RESULT, result);
        } catch (Throwable e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }
        return true;
    }

    @Override
    public String toString() {
        return "EvalExpression[" + expression + "]";
    }

    @Override
    public String getTraceLabel() {
        return "eval[" + expression + "]";
    }

}
