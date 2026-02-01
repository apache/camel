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
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.types.ChainOperatorType;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Represents chain operator expression in the AST.
 */
public class ChainExpression extends BaseSimpleNode {

    private static final String CHAIN_VARIABLE = "CamelSimpleChainParam";

    private final ChainOperatorType operator;
    private SimpleNode left;
    private final List<SimpleNode> right = new ArrayList<>();

    public ChainExpression(SimpleToken token) {
        super(token);
        operator = ChainOperatorType.asOperator(token.getText());
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(" " + token.getText() + " ");
        for (SimpleNode rn : right) {
            sj.add(rn.toString());
        }
        return left + " " + token.getText() + " " + sj;
    }

    public boolean acceptLeftNode(SimpleNode lef) {
        this.left = lef;
        return true;
    }

    public boolean acceptRightNode(SimpleNode right) {
        this.right.add(right);
        return true;
    }

    public ChainOperatorType getOperator() {
        return operator;
    }

    public SimpleNode getLeft() {
        return left;
    }

    public List<SimpleNode> getRight() {
        return right;
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        ObjectHelper.notNull(left, "left node", this);
        ObjectHelper.notNull(right, "right node", this);

        // prepare nodes as the chain may refer to other functions
        AtomicBoolean param = new AtomicBoolean();
        left = prepareChainNode(camelContext, left, param);
        final Expression leftExp = left.createExpression(camelContext, expression);

        final List<Expression> rightExp = new ArrayList<>();
        for (SimpleNode rn : right) {
            rn = prepareChainNode(camelContext, rn, param);
            final Expression right = rn.createExpression(camelContext, expression);
            if (param.get()) {
                rightExp.add(new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        exchange.setVariable(CHAIN_VARIABLE, exchange.getMessage().getBody());
                        try {
                            return right.evaluate(exchange, Object.class);
                        } finally {
                            exchange.removeVariable(CHAIN_VARIABLE);
                        }
                    }

                    @Override
                    public String toString() {
                        return right.toString();
                    }
                });
            } else {
                rightExp.add(right);
            }
        }

        if (operator == ChainOperatorType.CHAIN || operator == ChainOperatorType.CHAIN_NULL_SAFE) {
            boolean nullSafe = operator == ChainOperatorType.CHAIN_NULL_SAFE;
            return createChainExpression(camelContext, leftExp, rightExp, nullSafe);
        }

        throw new SimpleParserException("Unknown chain operator " + operator, token.getIndex());
    }

    private SimpleNode prepareChainNode(CamelContext camelContext, SimpleNode node, AtomicBoolean param) {
        param.set(false);
        if (node instanceof LiteralExpression le) {
            String text = le.getText();
            if (text.startsWith("$")) {
                // this may be a function
                String key = text.substring(1);
                key = StringHelper.before(key, "(", key);
                if (PluginHelper.getSimpleFunctionRegistry(camelContext).getCustomFunctionNames().contains(key)) {
                    String changed = text.replace("$" + key + "()", "function(" + key + ")");
                    if (changed.equals(text)) {
                        changed = text.replace("$" + key + "(", "function(" + key);
                    }
                    le.replaceText(changed);

                    SimpleFunctionStart sfs = new SimpleFunctionStart(le.getToken(), null, true);
                    sfs.getBlock().addChild(le);
                    node = sfs;
                }
            }
            text = le.getText();
            if (text.contains("$param")) {
                text = text.replace("$param", "${variable." + CHAIN_VARIABLE + "}");
                param.set(true);
                le.replaceText(text);
            }
            return node;
        } else if (node instanceof SimpleFunctionStart sf) {
            for (var child : sf.getBlock().getChildren()) {
                if (child instanceof LiteralExpression le) {
                    String text = le.getText();
                    if (text.contains("$param")) {
                        text = text.replace("$param", "${variable." + CHAIN_VARIABLE + "}");
                        param.set(true);
                        le.replaceText(text);
                    }
                }
            }
        }
        return node;
    }

    private Expression createChainExpression(
            final CamelContext camelContext, final Expression leftExp, final List<Expression> rightExp, boolean nullSafe) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // left is input to right
                Object value = leftExp.evaluate(exchange, Object.class);
                if (value == null && nullSafe) {
                    return null; // break out
                }

                Object originalBody = exchange.getMessage().getBody();
                try {
                    exchange.getMessage().setBody(value);
                    for (Expression exp : rightExp) {
                        value = exp.evaluate(exchange, Object.class);
                        if (value == null && nullSafe) {
                            return null; // break out
                        }
                        exchange.getMessage().setBody(value);
                    }
                    if (value == null) {
                        return null;
                    }
                    return camelContext.getTypeConverter().convertTo(type, exchange, value);
                } finally {
                    // restore original body to avoid side effects
                    exchange.getMessage().setBody(originalBody);
                }
            }

            @Override
            public String toString() {
                return left + " " + token.getText() + " " + right;
            }
        };
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        return BaseSimpleParser.CODE_START + doCreateCode(camelContext, expression) + BaseSimpleParser.CODE_END;
    }

    private String doCreateCode(CamelContext camelContext, String expression) throws SimpleParserException {
        throw new SimpleParserException("Chain operator " + operator + " not supported in csimple", token.getIndex());
    }

}
