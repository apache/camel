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
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;

/**
 *
 */
public final class ExpressionNodeRenderer {
    private ExpressionNodeRenderer() {
        // Utility class, no public or protected default constructor
    }

    public static void render(StringBuilder buffer, ProcessorDefinition<?> processor) {
        ExpressionNode expNode = (ExpressionNode)processor;
        buffer.append(".").append(expNode.getShortName());
        if (expNode instanceof DelayDefinition) {
            renderDelay(buffer, expNode);
        } else if (expNode instanceof FilterDefinition) {
            renderFilter(buffer, expNode);
        } else if (expNode instanceof IdempotentConsumerDefinition) {
            renderIdempotentConsumer(buffer, expNode);
        } else if (expNode instanceof LoopDefinition) {
            renderLoop(buffer, expNode);
        } else if (expNode instanceof RecipientListDefinition) {
            ExpressionDefinition expression = expNode.getExpression();
            buffer.append("(");
            ExpressionRenderer.render(buffer, expression);
            buffer.append(")");
        } else if (expNode instanceof SetBodyDefinition) {
            renderSetBody(buffer, expNode);
        } else if (expNode instanceof SetHeaderDefinition) {
            renderSetHeader(buffer, expNode);
        } else if (expNode instanceof SetOutHeaderDefinition) {
            // TODO unsupported expression node
            buffer.append("(\"setOutHeaderDefinition\")");
        } else if (expNode instanceof SetPropertyDefinition) {
            renderSetProperty(buffer, expNode);
        } else if (expNode instanceof SplitDefinition) {
            renderSplit(buffer, expNode);
        } else if (expNode instanceof TransformDefinition) {
            renderTransform(buffer, expNode);
        } else if (expNode instanceof WhenDefinition) {
            renderWhen(buffer, expNode);
        }
    }

    private static void renderDelay(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        String delay = expression.getExpressionValue().toString();
        if (!delay.contains("(")) {
            String delayTime = expression.getExpressionValue().toString();
            buffer.append("(").append(delayTime).append(")");
        } else {
            buffer.append("()");
            ExpressionRenderer.render(buffer, expression);
        }
    }

    private static void renderFilter(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        if (!(expression instanceof ExpressionClause)) {
            if (expression.getPredicate() != null) {
                buffer.append("(");
                PredicateRenderer.render(buffer, expression.getPredicate());
                buffer.append(")");
            }
        } else {
            String language = expression.getLanguage();
            if (language != null && !language.equals("")) {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            } else {
                buffer.append("()");
                ExpressionRenderer.render(buffer, expression);
            }
        }
    }

    private static void renderIdempotentConsumer(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        IdempotentConsumerDefinition idempotentConsume = (IdempotentConsumerDefinition)expNode;
        buffer.append("(");
        ExpressionRenderer.render(buffer, expression);
        buffer.append(", ");
        IdempotentRepository repository = idempotentConsume.getMessageIdRepository();
        if (repository instanceof FileIdempotentRepository) {
            // TODO need to be improved
            buffer.append("FileIdempotentRepository.fileIdempotentRepository()");
        } else if (repository instanceof MemoryIdempotentRepository) {
            buffer.append("MemoryIdempotentRepository.memoryIdempotentRepository()");
        }
        buffer.append(")");
        if (!idempotentConsume.isEager()) {
            buffer.append(".eager(false)");
        }
    }

    private static void renderLoop(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        if (expression instanceof ConstantExpression) {
            buffer.append("(").append(expression.getExpression()).append(")");
        } else {
            buffer.append("()");
            ExpressionRenderer.render(buffer, expression);
        }
    }

    private static void renderSetBody(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        String expValue = expression.getExpressionValue().toString();
        if (expValue.startsWith("append")) {
            buffer.append("(");
            ExpressionRenderer.render(buffer, expression);
            buffer.append(")");
        } else {
            buffer.append("()");
            ExpressionRenderer.renderConstant(buffer, expression);
        }
    }

    private static void renderSetHeader(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        SetHeaderDefinition set = (SetHeaderDefinition)expNode;
        buffer.append("(\"").append(set.getHeaderName()).append("\")");
        if (expression.getExpressionValue() != null) {
            ExpressionRenderer.renderConstant(buffer, expression);
        } else if (expression.getExpressionType() != null) {
            ExpressionRenderer.render(buffer, expression);
        }
    }

    private static void renderSetProperty(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
        SetPropertyDefinition set = (SetPropertyDefinition)expNode;
        buffer.append("(\"").append(set.getPropertyName()).append("\")");
        if (expression.getExpressionValue() != null) {
            ExpressionRenderer.renderConstant(buffer, expression);
        } else if (expression.getExpressionType() != null) {
            ExpressionRenderer.render(buffer, expression);
        }
    }

    private static void renderSplit(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
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
    }

    private static void renderTransform(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
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
    }

    private static void renderWhen(StringBuilder buffer, ExpressionNode expNode) {
        ExpressionDefinition expression = expNode.getExpression();
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
