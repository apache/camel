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
package org.apache.camel.language.jq;

import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.module.loaders.BuiltinModuleLoader;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.StaticService;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.util.ObjectHelper;

@Language("jq")
public class JqLanguage extends SingleInputTypedLanguageSupport implements StaticService {

    private Scope rootScope;

    @Override
    public void init() {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        if (this.rootScope == null) {
            this.rootScope = Scope.newEmptyScope();
            this.rootScope.setModuleLoader(BuiltinModuleLoader.getInstance());
            JqFunctions.load(getCamelContext(), rootScope);
            JqFunctions.loadLocal(rootScope);
        }
    }

    public Scope getRootScope() {
        return rootScope;
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void stop() {
        // noop
    }

    @Override
    public Predicate createPredicate(String expression) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression));
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return ExpressionToPredicateAdapter.toPredicate(createExpression(expression, properties));
    }

    @Override
    public Expression createExpression(String expression) {
        JqExpression answer = new JqExpression(Scope.newChildScope(rootScope), expression);
        answer.setResultType(getResultType());
        answer.setHeaderName(getHeaderName());
        answer.setPropertyName(getPropertyName());
        answer.init(getCamelContext());
        return answer;
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        JqExpression answer = new JqExpression(Scope.newChildScope(rootScope), expression);
        answer.setResultType(property(Class.class, properties, 0, getResultType()));
        answer.setHeaderName(property(String.class, properties, 1, getHeaderName()));
        answer.setPropertyName(property(String.class, properties, 2, getPropertyName()));
        answer.init(getCamelContext());
        return answer;
    }
}
