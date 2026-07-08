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
package org.apache.camel.component.a2a.simple;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.a2a.A2AEndpoint;
import org.apache.camel.component.a2a.A2AProgress;
import org.apache.camel.component.a2a.A2ATypeConverters;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.Skill;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.apache.camel.support.ExpressionAdapter;

/**
 * Static factory methods that return {@link Expression} instances for the {@code a2a:} Simple language function
 * namespace. Each expression evaluates at runtime with full {@link Exchange} access.
 */
public final class A2ASimpleExpressionBuilder {

    private A2ASimpleExpressionBuilder() {
    }

    /**
     * Creates an expression that emits a progress update via {@link A2AProgress}.
     *
     * @param stateStr   the task state name (e.g. "WORKING"), or {@code null} for the default {@link TaskState#WORKING}
     * @param messageExp the progress message, which may contain Simple expressions like {@code ${body}}
     */
    public static Expression emitProgress(String stateStr, String messageExp) {
        return new ExpressionAdapter() {
            private Expression innerExp;

            @Override
            public void init(CamelContext context) {
                if (messageExp != null && messageExp.contains("${")) {
                    innerExp = context.resolveLanguage("simple").createExpression(messageExp);
                    innerExp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String message;
                if (innerExp != null) {
                    message = innerExp.evaluate(exchange, String.class);
                } else {
                    message = messageExp;
                }
                TaskState state = stateStr != null ? TaskState.valueOf(stateStr) : TaskState.WORKING;
                A2AProgress.emit(exchange, state, message);
                return message;
            }

            @Override
            public String toString() {
                if (stateStr != null) {
                    return "a2a:emit(" + stateStr + ", " + messageExp + ")";
                }
                return "a2a:emit(" + messageExp + ")";
            }
        };
    }

    /**
     * Creates an expression that extracts {@link org.apache.camel.component.a2a.model.TextPart} content from A2A Task
     * or Message objects.
     *
     * @param inputExp optional Simple expression for the input; if {@code null}, uses the message body
     */
    public static Expression extractText(String inputExp) {
        return createExtractExpression(inputExp, "text");
    }

    /**
     * Creates an expression that extracts {@link org.apache.camel.component.a2a.model.DataPart} content from A2A Task
     * or Message objects.
     *
     * @param inputExp optional Simple expression for the input; if {@code null}, uses the message body
     */
    public static Expression extractData(String inputExp) {
        return createExtractExpression(inputExp, "data");
    }

    /**
     * Creates an expression that extracts {@link org.apache.camel.component.a2a.model.FilePart} content from A2A Task
     * or Message objects.
     *
     * @param inputExp optional Simple expression for the input; if {@code null}, uses the message body
     */
    public static Expression extractFile(String inputExp) {
        return createExtractExpression(inputExp, "file");
    }

    /**
     * Creates an expression that extracts a field from the resolved {@link AgentCard} on the A2A endpoint.
     *
     * @param field the card field to extract (e.g. "name", "skills", "skills.json"), or {@code null} for the full card
     *              as JSON
     */
    public static Expression extractCardField(String field) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                AgentCard card = resolveCard(exchange);
                if (card == null) {
                    return "";
                }
                if (field == null || field.isEmpty()) {
                    return serializeToJson(card);
                }
                return switch (field) {
                    case "name" -> nullSafe(card.getName());
                    case "description" -> nullSafe(card.getDescription());
                    case "url" -> nullSafe(card.getUrl());
                    case "version" -> nullSafe(card.getVersion());
                    case "iconUrl" -> nullSafe(card.getIconUrl());
                    case "documentationUrl" -> nullSafe(card.getDocumentationUrl());
                    case "provider" -> serializeToJson(card.getProvider());
                    case "capabilities" -> serializeToJson(card.getCapabilities());
                    case "skills" -> formatSkillsText(card.getSkills());
                    case "skills.json" -> serializeToJson(card.getSkills());
                    case "supportedInterfaces" -> serializeToJson(card.getSupportedInterfaces());
                    case "securitySchemes" -> serializeToJson(card.getSecuritySchemes());
                    case "securityRequirements" -> serializeToJson(card.getSecurityRequirements());
                    case "security" -> serializeToJson(card.getSecurity());
                    default -> "";
                };
            }

            @Override
            public String toString() {
                return field != null ? "a2a:card." + field : "a2a:card";
            }
        };
    }

    private static AgentCard resolveCard(Exchange exchange) {
        Endpoint from = exchange.getFromEndpoint();
        if (from instanceof A2AEndpoint a2aFrom) {
            AgentCard card = a2aFrom.getResolvedCard();
            if (card != null) {
                return card;
            }
        }
        for (Endpoint ep : exchange.getContext().getEndpoints()) {
            if (ep instanceof A2AEndpoint a2aEp) {
                AgentCard card = a2aEp.getResolvedCard();
                if (card != null) {
                    return card;
                }
            }
        }
        return null;
    }

    private static String formatSkillsText(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Skill skill : skills) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("* ").append(skill.getName());
            if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                sb.append(": ").append(skill.getDescription());
            }
        }
        return sb.toString();
    }

    private static String serializeToJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return A2AJsonMapper.instance().writeValueAsString(value);
        } catch (Exception e) {
            return "";
        }
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static Expression createExtractExpression(String inputExp, String type) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (inputExp != null && !inputExp.isEmpty()) {
                    exp = context.resolveLanguage("simple").createExpression(inputExp);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Object input;
                if (exp != null) {
                    input = exp.evaluate(exchange, Object.class);
                } else {
                    input = exchange.getMessage().getBody();
                }

                return switch (type) {
                    case "text" -> extractTextFrom(input);
                    case "data" -> extractDataFrom(input);
                    case "file" -> extractFileFrom(input);
                    default -> "";
                };
            }

            @Override
            public String toString() {
                if (inputExp != null) {
                    return "a2a:" + type + "(" + inputExp + ")";
                }
                return "a2a:" + type;
            }
        };
    }

    private static String extractTextFrom(Object input) {
        if (input instanceof Task task) {
            return A2ATypeConverters.extractText(task);
        } else if (input instanceof Message message) {
            return A2ATypeConverters.extractText(message);
        }
        return input != null ? input.toString() : "";
    }

    private static String extractDataFrom(Object input) {
        if (input instanceof Task task) {
            return A2ATypeConverters.extractData(task);
        } else if (input instanceof Message message) {
            return A2ATypeConverters.extractData(message);
        }
        return "";
    }

    private static String extractFileFrom(Object input) {
        if (input instanceof Task task) {
            return A2ATypeConverters.extractFile(task);
        } else if (input instanceof Message message) {
            return A2ATypeConverters.extractFile(message);
        }
        return "";
    }
}
