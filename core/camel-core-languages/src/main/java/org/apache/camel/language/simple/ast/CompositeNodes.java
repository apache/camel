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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A node which contains other {@link SimpleNode nodes}.
 */
public class CompositeNodes extends BaseSimpleNode {

    private final List<SimpleNode> children = new ArrayList<>();

    public CompositeNodes(SimpleToken token) {
        super(token);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (SimpleNode child : children) {
            sb.append(child.toString());
        }
        return sb.toString();
    }

    public void addChild(SimpleNode child) {
        children.add(child);
    }

    public List<SimpleNode> getChildren() {
        return children;
    }

    @Override
    public Expression createExpression(String expression) {
        if (children.isEmpty()) {
            return null;
        } else if (children.size() == 1) {
            return children.get(0).createExpression(expression);
        } else {
            List<Expression> answer = new ArrayList<>();
            for (SimpleNode child : children) {
                answer.add(child.createExpression(expression));
            }
            return ExpressionBuilder.concatExpression(answer);
        }
    }

}
