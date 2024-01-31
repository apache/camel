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
package org.apache.camel.builder;

import org.apache.camel.model.language.CSimpleExpression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.Hl7TerserExpression;
import org.apache.camel.model.language.JavaExpression;
import org.apache.camel.model.language.JavaScriptExpression;
import org.apache.camel.model.language.JoorExpression;
import org.apache.camel.model.language.JqExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.MvelExpression;
import org.apache.camel.model.language.OgnlExpression;
import org.apache.camel.model.language.PythonExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.SpELExpression;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.VariableExpression;
import org.apache.camel.model.language.WasmExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;

/**
 * {@code LanguageBuilderFactory} is a factory class of builder of all supported languages.
 */
public final class LanguageBuilderFactory {

    /**
     * Uses the Constant language
     */
    public ConstantExpression.Builder constant() {
        return new ConstantExpression.Builder();
    }

    /**
     * Uses the CSimple language
     */
    public CSimpleExpression.Builder csimple() {
        return new CSimpleExpression.Builder();
    }

    /**
     * Uses the Datasonnet language
     */
    public DatasonnetExpression.Builder datasonnet() {
        return new DatasonnetExpression.Builder();
    }

    /**
     * Uses the ExchangeProperty language
     */
    public ExchangePropertyExpression.Builder exchangeProperty() {
        return new ExchangePropertyExpression.Builder();
    }

    /**
     * Uses the Groovy language
     */
    public GroovyExpression.Builder groovy() {
        return new GroovyExpression.Builder();
    }

    /**
     * Uses the Header language
     */
    public HeaderExpression.Builder header() {
        return new HeaderExpression.Builder();
    }

    /**
     * Uses the Hl7Terser language
     */
    public Hl7TerserExpression.Builder hl7terser() {
        return new Hl7TerserExpression.Builder();
    }

    /**
     * Uses the JavaScript language
     */
    public JavaScriptExpression.Builder js() {
        return new JavaScriptExpression.Builder();
    }

    /**
     * Uses the Java language
     */
    public JavaExpression.Builder java() {
        return new JavaExpression.Builder();
    }

    /**
     * Uses the JOOR language
     */
    @Deprecated
    public JoorExpression.Builder joor() {
        return new JoorExpression.Builder();
    }

    /**
     * Uses the JQ language
     */
    public JqExpression.Builder jq() {
        return new JqExpression.Builder();
    }

    /**
     * Uses the JsonPath language
     */
    public JsonPathExpression.Builder jsonpath() {
        return new JsonPathExpression.Builder();
    }

    /**
     * Uses a custom language
     */
    public LanguageExpression.Builder language() {
        return new LanguageExpression.Builder();
    }

    /**
     * Uses the MethodCall language
     */
    public MethodCallExpression.Builder bean() {
        return new MethodCallExpression.Builder();
    }

    /**
     * Uses the Mvel language
     */
    public MvelExpression.Builder mvel() {
        return new MvelExpression.Builder();
    }

    /**
     * Uses the Ognl language
     */
    public OgnlExpression.Builder ognl() {
        return new OgnlExpression.Builder();
    }

    /**
     * Uses the Python language
     */
    public PythonExpression.Builder python() {
        return new PythonExpression.Builder();
    }

    /**
     * Uses the Ref language
     */
    public RefExpression.Builder ref() {
        return new RefExpression.Builder();
    }

    /**
     * Uses the Simple language
     */
    public SimpleExpression.Builder simple() {
        return new SimpleExpression.Builder();
    }

    /**
     * Uses the SpEL language
     */
    public SpELExpression.Builder spel() {
        return new SpELExpression.Builder();
    }

    /**
     * Uses the Tokenizer language
     */
    public TokenizerExpression.Builder tokenize() {
        return new TokenizerExpression.Builder();
    }

    /**
     * Uses the Variable language
     */
    public VariableExpression.Builder variable() {
        return new VariableExpression.Builder();
    }

    /**
     * Uses the XMLTokenizer language
     */
    public XMLTokenizerExpression.Builder xtokenize() {
        return new XMLTokenizerExpression.Builder();
    }

    /**
     * Uses the XPath language
     */
    public XPathExpression.Builder xpath() {
        return new XPathExpression.Builder();
    }

    /**
     * Uses the XQuery language
     */
    public XQueryExpression.Builder xquery() {
        return new XQueryExpression.Builder();
    }

    /**
     * Uses the Wasm language
     */
    public WasmExpression.Builder wasm() {
        return new WasmExpression.Builder();
    }
}
