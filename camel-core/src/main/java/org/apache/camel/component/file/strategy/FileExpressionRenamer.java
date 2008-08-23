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

import java.io.File;

import org.apache.camel.Expression;
import org.apache.camel.component.file.FileExchange;

/**
 * File renamed using {@link Expression} to dynamically compute the file name.
 * <p/>
 * If most cases the {@link org.apache.camel.language.simple.FileLanguage FileLanguage} is used to
 * create the expressions.
 */
public class FileExpressionRenamer implements FileRenamer {

    private static final boolean ON_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private Expression expression;

    public File renameFile(FileExchange exchange, File file) {
        if (expression == null) {
            throw new IllegalArgumentException("Expression is not set");
        }
        File parent = file.getParentFile();

        Object result = expression.evaluate(exchange);
        String name = exchange.getContext().getTypeConverter().convertTo(String.class, result);

        if (ON_WINDOWS && (name.indexOf(":") >= 0 || name.startsWith("//"))) {
            return new File(name);
        }
        return new File(parent, name);
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }
}
