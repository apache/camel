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
package org.apache.camel.reifier.language;

import org.apache.camel.CamelContext;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.TokenizerExpression;

public class TokenizerExpressionReifier extends SingleInputTypedExpressionReifier<TokenizerExpression> {

    public TokenizerExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, definition);
    }

    protected Object[] createProperties() {
        Object[] properties = new Object[11];
        properties[0] = asResultType();
        properties[1] = parseString(definition.getSource());
        Boolean regex = parseBoolean(definition.getRegex());
        // special for new line, carriage return and tab in tokens, if defined from XML then they are 2
        // characters, so we replace them back to a single char. A regular expression understands them
        // as they are, so only a leading new line is replaced there
        String token = parseString(definition.getToken());
        if (token != null && Boolean.TRUE.equals(regex)) {
            if (token.startsWith("\\n")) {
                token = '\n' + token.substring(2);
            }
        } else if (token != null) {
            token = token.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
        }
        properties[2] = token;
        properties[3] = parseString(definition.getEndToken());
        properties[4] = parseString(definition.getInheritNamespaceTagName());
        properties[5] = parseString(definition.getGroupDelimiter());
        properties[6] = regex;
        properties[7] = parseBoolean(definition.getXml());
        properties[8] = parseBoolean(definition.getIncludeTokens());
        properties[9] = parseString(definition.getGroup());
        properties[10] = parseBoolean(definition.getSkipFirst());
        return properties;
    }

}
