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
package org.apache.camel.language.simple;

import java.util.List;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.language.simple.ast.BinaryExpression;
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.SimpleFunctionStart;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.apache.camel.language.simple.ast.SingleQuoteStart;
import org.apache.camel.language.simple.types.BinaryOperatorType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimplePredicateParserNodesTest extends ExchangeTestSupport {

    @Test
    public void testParserNodes() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(null, "${body} == 'foo'", true, null);
        List<SimpleNode> nodes = parser.parseTokens();
        Assertions.assertEquals(1, nodes.size());
        BinaryExpression be = (BinaryExpression) nodes.get(0);

        Assertions.assertEquals(BinaryOperatorType.EQ, be.getOperator());

        SingleQuoteStart qe = (SingleQuoteStart) be.getRight();
        LiteralNode ln = (LiteralNode) qe.getBlock().getChildren().get(0);
        Assertions.assertEquals("foo", ln.getText());

        SimpleFunctionStart fe = (SimpleFunctionStart) be.getLeft();
        ln = (LiteralNode) fe.getBlock().getChildren().get(0);
        Assertions.assertEquals("body", ln.toString());
    }

    @Test
    public void testParserNodesEmbeddedFunction() throws Exception {
        exchange.getIn().setBody("foo");

        SimplePredicateParser parser = new SimplePredicateParser(null, "${body} != 'Hello ${header.bar}'", true, null);
        List<SimpleNode> nodes = parser.parseTokens();
        Assertions.assertEquals(1, nodes.size());
        BinaryExpression be = (BinaryExpression) nodes.get(0);

        Assertions.assertEquals(BinaryOperatorType.NOT_EQ, be.getOperator());

        SingleQuoteStart qe = (SingleQuoteStart) be.getRight();
        LiteralNode ln = (LiteralNode) qe.getBlock().getChildren().get(0);
        Assertions.assertEquals("Hello ", ln.getText());

        SimpleFunctionStart fe = (SimpleFunctionStart) qe.getBlock().getChildren().get(1);
        ln = (LiteralNode) fe.getBlock().getChildren().get(0);
        Assertions.assertEquals("header.bar", ln.toString());
    }

}
