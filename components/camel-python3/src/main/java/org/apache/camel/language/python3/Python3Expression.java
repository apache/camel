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
package org.apache.camel.language.python3;

import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionAdapter;

/**
 * A Python 3 script usable as both a Camel {@link org.apache.camel.Expression} and {@link org.apache.camel.Predicate}.
 */
public class Python3Expression extends ExpressionAdapter {

    private final String text;
    private volatile Python3Language language;

    public Python3Expression(String text) {
        this(text, null);
    }

    Python3Expression(String text, Python3Language language) {
        this.text = text;
        this.language = language;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        Python3Language lang = language;
        if (lang == null) {
            lang = (Python3Language) exchange.getContext().resolveLanguage("python3");
            language = lang;
        }
        return lang.evaluateExpression(text, exchange);
    }

    @Override
    public String toString() {
        return "python3: " + text;
    }
}
