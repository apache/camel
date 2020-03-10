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
package org.apache.camel.language.simple.ast;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * Starts a block enclosed by single quotes
 */
public class SingleQuoteStart extends BaseSimpleNode implements BlockStart {

    private final CompositeNodes block;

    public SingleQuoteStart(SimpleToken token) {
        super(token);
        this.block = new CompositeNodes(token);
    }

    @Override
    public String toString() {
        // output a nice toString so it makes debugging easier as we can see the entire block
        return "'" + block + "'";
    }

    @Override
    public Expression createExpression(String expression) {
        Expression answer = null;
        if (block != null) {
            answer = block.createExpression(expression);
        }
        if (answer == null) {
            // there quoted literal is empty
            answer = ExpressionBuilder.constantExpression("");
        }
        return answer;
    }

    @Override
    public boolean acceptAndAddNode(SimpleNode node) {
        block.addChild(node);
        return true;
    }

}
