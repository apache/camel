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
package org.apache.camel.language.joor;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.support.ExpressionAdapter;

public class JoorExpression extends ExpressionAdapter {

    private final String text;
    private JoorCompiler compiler;
    private JoorMethod method;

    private Class<?> resultType;
    private boolean preCompile = true;
    private boolean singleQuotes = true;

    public JoorExpression(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "joor:" + text;
    }

    public JoorCompiler getCompiler() {
        return compiler;
    }

    public void setCompiler(JoorCompiler compiler) {
        this.compiler = compiler;
    }

    public boolean isPreCompile() {
        return preCompile;
    }

    public void setPreCompile(boolean preCompile) {
        this.preCompile = preCompile;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isSingleQuotes() {
        return singleQuotes;
    }

    public void setSingleQuotes(boolean singleQuotes) {
        this.singleQuotes = singleQuotes;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        JoorMethod target = this.method;
        if (target == null) {
            target = compiler.compile(exchange.getContext(), text, singleQuotes);
        }
        // optimize as we call the same method all the time so we dont want to find the method every time as joor would do
        // if you use its call method
        Object body = exchange.getIn().getBody();
        // in the rare case the body is already an optional
        Optional<?> optional = body instanceof Optional ? (Optional<?>) body : Optional.ofNullable(body);
        Object out;
        try {
            out = target.evaluate(exchange.getContext(), exchange, exchange.getIn(), body, optional);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(this, exchange, e);
        }
        if (out != null && resultType != null) {
            return exchange.getContext().getTypeConverter().convertTo(resultType, exchange, out);
        } else {
            return out;
        }
    }

    @Override
    public void init(CamelContext context) {
        super.init(context);

        if (preCompile && this.method == null) {
            this.method = compiler.compile(context, text, singleQuotes);
        }
    }

}
