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
import org.apache.camel.language.simple.ast.LiteralNode;
import org.apache.camel.language.simple.ast.SimpleFunctionStart;
import org.apache.camel.language.simple.ast.SimpleNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleExpressionParserNodesTest extends ExchangeTestSupport {

    @Test
    public void testParserNodes() throws Exception {
        exchange.getIn().setBody("foo");

        SimpleExpressionParser parser = new SimpleExpressionParser(null, "Hello ${body}", true, null);
        List<SimpleNode> nodes = parser.parseTokens();
        Assertions.assertEquals(2, nodes.size());
        LiteralNode ln = (LiteralNode) nodes.get(0);
        Assertions.assertEquals("Hello ", ln.getText());
        SimpleFunctionStart fe = (SimpleFunctionStart) nodes.get(1);
        ln = (LiteralNode) fe.getBlock().getChildren().get(0);
        Assertions.assertEquals("body", ln.toString());
    }

    @Test
    public void testParserNodesEmbeddedFunction() throws Exception {
        exchange.getIn().setBody("foo");

        SimpleExpressionParser parser = new SimpleExpressionParser(null, "Hello ${body} and ${header.bar}", true, null);
        List<SimpleNode> nodes = parser.parseTokens();
        Assertions.assertEquals(4, nodes.size());
        LiteralNode ln = (LiteralNode) nodes.get(0);
        Assertions.assertEquals("Hello ", ln.getText());
        SimpleFunctionStart fe = (SimpleFunctionStart) nodes.get(1);
        ln = (LiteralNode) fe.getBlock().getChildren().get(0);
        Assertions.assertEquals("body", ln.toString());
        ln = (LiteralNode) nodes.get(2);
        Assertions.assertEquals(" and ", ln.getText());
        fe = (SimpleFunctionStart) nodes.get(3);
        ln = (LiteralNode) fe.getBlock().getChildren().get(0);
        Assertions.assertEquals("header.bar", ln.toString());
    }

}
