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
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;

/**
 * Represents a node in the Simple AST
 */
public interface SimpleNode {

    /**
     * Gets the token by which this model was based upon
     *
     * @return the token
     */
    SimpleToken getToken();

    /**
     * Creates a Camel {@link Expression} based on this model.
     *
     * @param expression the input string
     * @return the created {@link Expression}
     * @throws org.apache.camel.language.simple.types.SimpleParserException
     *          should be thrown if error parsing the model
     */
    Expression createExpression(String expression) throws SimpleParserException;

}
