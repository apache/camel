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
package org.apache.camel.builder;

import org.apache.camel.Expression;
import org.apache.camel.support.builder.Namespaces;

/**
 * A builder of expressions or predicates based on values.
 */
public class ValueBuilder extends org.apache.camel.support.builder.ValueBuilder {

    public ValueBuilder(Expression expression) {
        super(expression);
    }

    // Expression builders
    // -------------------------------------------------------------------------

    public ValueBuilder tokenizeXML(String tagName, String inheritNamespaceTagName) {
        Expression newExp = ExpressionBuilder.tokenizeXMLExpression(tagName, inheritNamespaceTagName);
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder xtokenize(String path, Namespaces namespaces) {
        return xtokenize(path, 'i', namespaces);
    }

    public ValueBuilder xtokenize(String path, char mode, Namespaces namespaces) {
        Expression newExp = ExpressionBuilder.tokenizeXMLAwareExpression(path, mode, 1, namespaces);
        return onNewValueBuilder(newExp);
    }

    public ValueBuilder tokenizePair(String startToken, String endToken, boolean includeTokens) {
        Expression newExp = ExpressionBuilder.tokenizePairExpression(startToken, endToken, includeTokens);
        return onNewValueBuilder(newExp);
    }

    @Override
    protected ValueBuilder onNewValueBuilder(Expression exp) {
        return new ValueBuilder(exp);
    }
}
