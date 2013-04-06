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
package org.apache.camel.component.file.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.util.ObjectHelper;

public class GenericFileExpressionRenamer<T> implements GenericFileRenamer<T> {
    private Expression expression;

    public GenericFileExpressionRenamer() {
    }

    public GenericFileExpressionRenamer(Expression expression) {
        this.expression = expression;
    }

    public GenericFile<T> renameFile(Exchange exchange, GenericFile<T> file) {
        ObjectHelper.notNull(expression, "expression");

        String newName = expression.evaluate(exchange, String.class);

        // make a copy as result and change its file name
        GenericFile<T> result = file.copyFrom(file);
        result.changeFileName(newName);
        return result;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }    
}
