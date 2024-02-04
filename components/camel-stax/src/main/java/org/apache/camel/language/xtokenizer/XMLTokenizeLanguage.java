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
package org.apache.camel.language.xtokenizer;

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.SingleInputTypedLanguageSupport;
import org.apache.camel.support.builder.Namespaces;

/**
 * A language for tokenizer expressions.
 * <p/>
 * This xmltokenizer language can operate in the following modes:
 * <ul>
 * <li>i - injecting the contextual namespace bindings into the extracted token (default)</li>
 * <li>w - wrapping the extracted token in its ancestor context</li>
 * <li>u - unwrapping the extracted token to its child content</li>
 * <li>t - extracting the text content of the specified element</li>
 * </ul>
 */
@Language("xtokenize")
public class XMLTokenizeLanguage extends SingleInputTypedLanguageSupport {

    @Override
    public Expression createExpression(Expression source, String expression, Object[] properties) {
        Character mode = property(Character.class, properties, 4, "i");
        XMLTokenExpressionIterator answer = new XMLTokenExpressionIterator(source, expression, mode);
        answer.setGroup(property(int.class, properties, 5, 1));
        Object obj = properties[6];
        if (obj != null) {
            Namespaces ns;
            if (obj instanceof Namespaces) {
                ns = (Namespaces) obj;
            } else if (obj instanceof Map) {
                ns = new Namespaces();
                ((Map<String, String>) obj).forEach(ns::add);
            } else {
                throw new IllegalArgumentException(
                        "Namespaces is not instance of java.util.Map or " + Namespaces.class.getName());
            }
            answer.setNamespaces(ns.getNamespaces());
        }
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

}
