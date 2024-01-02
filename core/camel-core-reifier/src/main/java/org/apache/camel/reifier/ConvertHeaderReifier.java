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
package org.apache.camel.reifier;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ConvertHeaderDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.processor.ConvertHeaderProcessor;

public class ConvertHeaderReifier extends ProcessorReifier<ConvertHeaderDefinition> {

    public ConvertHeaderReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, ConvertHeaderDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        String key = parseString(definition.getName());
        Expression nameExpr;
        if (LanguageSupport.hasSimpleFunction(key)) {
            nameExpr = camelContext.resolveLanguage("simple").createExpression(key);
        } else {
            nameExpr = camelContext.resolveLanguage("constant").createExpression(key);
        }
        nameExpr.init(camelContext);

        String toKey = parseString(definition.getToName());
        Expression toNameExpr = null;
        if (toKey != null) {
            if (LanguageSupport.hasSimpleFunction(toKey)) {
                toNameExpr = camelContext.resolveLanguage("simple").createExpression(toKey);
            } else {
                toNameExpr = camelContext.resolveLanguage("constant").createExpression(toKey);
            }
            toNameExpr.init(camelContext);
        }

        Class<?> typeClass = parse(Class.class, or(definition.getTypeClass(), parseString(definition.getType())));
        String charset = validateCharset(parseString(definition.getCharset()));
        boolean mandatory = true;
        if (definition.getMandatory() != null) {
            mandatory = parseBoolean(definition.getMandatory(), true);
        }
        return new ConvertHeaderProcessor(key, nameExpr, toKey, toNameExpr, typeClass, charset, mandatory);
    }

    public static String validateCharset(String charset) throws UnsupportedCharsetException {
        if (charset != null) {
            if (Charset.isSupported(charset)) {
                return Charset.forName(charset).name();
            }
            throw new UnsupportedCharsetException(charset);
        }
        return null;
    }

}
