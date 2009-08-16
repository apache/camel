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

package org.apache.camel.web.util;

import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetOutHeaderDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 *
 */
public final class ExpressionNodeRenderer {
    private ExpressionNodeRenderer() {
        // Utility class, no public or protected default constructor
    }    

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        ExpressionNode expNode = (ExpressionNode)processor;
        ExpressionDefinition expression = expNode.getExpression();

        buffer.append(".").append(expNode.getShortName());
        if (expNode instanceof DelayDefinition) {
            String delay = expression.getExpressionValue().toString();
            if (!delay.contains("(")) {
                String delayTime = expression.getExpressionValue().toString();
                buffer.append("(").append(delayTime).append(")");
            } else {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            }
        } else if (expNode instanceof FilterDefinition) {
            if (expression.getPredicate() != null) {
                buffer.append("(");
                PredicateRenderer.render(buffer, expression.getPredicate());
                buffer.append(")");
            } else if (expression.getLanguage() != null) {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            } else {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            }
        } else if (expNode instanceof IdempotentConsumerDefinition) {
            // TODO improve it
        } else if (expNode instanceof LoopDefinition) {
            if (expression instanceof ConstantExpression) {
                buffer.append("(").append(expression.getExpression()).append(")");
            } else {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            }
        } else if (expNode instanceof RecipientListDefinition) {
            buffer.append("(");
            ExpressionRenderer.render(buffer, expression);
            buffer.append(")");
        } else if (expNode instanceof SetBodyDefinition) {
            String expValue = expression.getExpressionValue().toString();
            if (expValue.startsWith("append")) {
                buffer.append("(");
                ExpressionRenderer.render(buffer, expression);
                buffer.append(")");
            } else {
                buffer.append("()");
                ExpressionRenderer.renderConstant(buffer, expression);
            }
        } else if (expNode instanceof SetHeaderDefinition) {
            SetHeaderDefinition set = (SetHeaderDefinition)expNode;
            buffer.append("(\"").append(set.getHeaderName()).append("\")");
            if (expression.getExpressionValue() != null) {
                ExpressionRenderer.renderConstant(buffer, expression);
            } else if (expression.getExpressionType() != null) {
                ExpressionRenderer.render(buffer, expression);
            }
        } else if (expNode instanceof SetOutHeaderDefinition) {
            buffer.append("(\"unspported expressions in SetOutHeaderDefinition\")");
        } else if (expNode instanceof SetPropertyDefinition) {
            SetPropertyDefinition set = (SetPropertyDefinition)expNode;
            buffer.append("(\"").append(set.getPropertyName()).append("\")");
            if (expression.getExpressionValue() != null) {
                ExpressionRenderer.renderConstant(buffer, expression);
            } else if (expression.getExpressionType() != null) {
                ExpressionRenderer.render(buffer, expression);
            }
        } else if (expNode instanceof SplitDefinition) {
            if (expression.getExpressionValue() != null) {
                buffer.append("(");
                ExpressionRenderer.render(buffer, expression);
                buffer.append(")");
            } else if (expression.getExpressionType() != null) {
                buffer.append("().");
                ExpressionRenderer.render(buffer, expression);
            }

            SplitDefinition split = (SplitDefinition)expNode;
            if (split.isStreaming()) {
                buffer.append(".streaming()");
            }
        } else if (expNode instanceof TransformDefinition) {
            String expValue = expression.getExpressionValue().toString();
            if (expValue.startsWith("append") || expValue.startsWith("prepend") || expValue.startsWith("to")) {
                buffer.append("(");
                ExpressionRenderer.render(buffer, expression);
                buffer.append(")");
            } else if (expValue.startsWith("xpath")) {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            } else {
                buffer.append("(constant(\"").append(expression.getExpressionValue().toString()).append("\"))");
            }
        } else if (expNode instanceof WhenDefinition) {
            if (expression.getPredicate() != null) {
                buffer.append("(");
                PredicateRenderer.render(buffer, expression.getPredicate());
                buffer.append(")");
            }
            if (expression instanceof ExpressionClause) {
                buffer.append("()");
                ExpressionRenderer.render(buffer, (ExpressionClause)expression);
            }
        }
    }
}
