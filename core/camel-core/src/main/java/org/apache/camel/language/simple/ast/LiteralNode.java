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

/**
 * Represents a node in the AST which contains literals
 */
public interface LiteralNode extends SimpleNode {

    /**
     * Adds the given text to this model.
     * <p/>
     * This operation can be invoked multiple times to add more text.
     *
     * @param text the text to add
     */
    void addText(String text);

    /**
     * Gets the text
     *
     * @return the text, will never be <tt>null</tt>, but may contain an empty string.
     */
    String getText();

    /**
     * Whether to quote embedded nodes.
     * <p/>
     * Some functions such as the <tt>bean:</tt> function would need to quote its embedded nodes
     * as they are parameter values for method names.
     */
    boolean quoteEmbeddedNodes();

}
