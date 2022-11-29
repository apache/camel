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
import org.apache.camel.model.language.JsonPathExpression;

public class JsonPathExpressionReifier extends SingleInputTypedExpressionReifier<JsonPathExpression> {

    public JsonPathExpressionReifier(CamelContext camelContext, ExpressionDefinition definition) {
        super(camelContext, definition);
    }

    @Override
    protected Object[] createProperties() {
        Object[] properties = new Object[9];
        properties[0] = definition.getResultType();
        properties[1] = parseBoolean(definition.getSuppressExceptions());
        properties[2] = parseBoolean(definition.getAllowSimple());
        properties[3] = parseBoolean(definition.getAllowEasyPredicate());
        properties[4] = parseBoolean(definition.getWriteAsString());
        properties[5] = parseBoolean(definition.getUnpackArray());
        properties[6] = parseString(definition.getHeaderName());
        properties[7] = parseString(definition.getOption());
        properties[8] = parseString(definition.getPropertyName());
        return properties;
    }

}
