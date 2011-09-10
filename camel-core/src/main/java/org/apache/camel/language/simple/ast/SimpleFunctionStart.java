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
package org.apache.camel.language.simple.ast;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.SimpleToken;

/**
 * Starts a function
 */
public class SimpleFunctionStart extends BaseSimpleNode implements BlockStart {

    private LiteralNode literal;

    public SimpleFunctionStart(SimpleToken token) {
        super(token);
    }

    @Override
    public String toString() {
        // output a nice toString so it makes debugging easier as we can see the entire block
        return "${" + literal + "}";
    }

    @Override
    public Expression createExpression(String expression) {
        SimpleFunctionExpression function = new SimpleFunctionExpression(this.getToken());
        function.addText(literal.getText());
        return function.createExpression(expression);
    }

    @Override
    public boolean acceptAndAddNode(SimpleNode node) {
        // only accept literals as it contains the text for the function
        if (node instanceof LiteralNode) {
            literal = (LiteralNode) node;
            return true;
        } else {
            return false;
        }
    }

}
