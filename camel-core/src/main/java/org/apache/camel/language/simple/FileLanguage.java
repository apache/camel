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
package org.apache.camel.language.simple;

import org.apache.camel.Expression;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.language.IllegalSyntaxException;
import org.apache.camel.util.ObjectHelper;

/**
 * File language is an extension to Simple language to add file specific expressions.
 *
 * Examples of supported file expressions are:
 * <ul>
 * <li><tt>file:name</tt> to access the file name</li>
 * <li><tt>file:name.noext</tt> to access the file name with no extension</li>
 * <li><tt>file:parent</tt> to access the parent file name</li>
 * <li><tt>file:path</tt> to access the file path name</li>
 * <li><tt>file:absolute.path</tt> to access the absolute file path name</li>
 * <li><tt>file:canonical.path</tt> to access the canonical path name</li>
 * <li><tt>file:length</tt> to access the file length as a Long type</li>
 * <li><tt>date:&lt;command&gt;:&lt;pattern&gt;</tt> for date formatting using the {@link java.text.SimpleDateFormat} patterns.
 *     Additional Supported commands are: <tt>file</tt> for the last modified timestamp of the file.
 *     All the commands from {@link SimpleLanguage} is also avaiable.
 * </li>
 * </ul>
 * All the simple expression is also available so you can eg use <tt>${in.header.foo}</tt> to access the foo header.
 *
 * @see org.apache.camel.language.simple.SimpleLanguage
 * @see org.apache.camel.language.bean.BeanLanguage
 */
public class FileLanguage extends AbstractSimpleLanguage {

    public static Expression file(String expression) {
        FileLanguage language = new FileLanguage();
        return language.createExpression(expression);
    }

    protected Expression<FileExchange> createSimpleExpression(String expression) {

        // file: prefix
        String remainder = ifStartsWithReturnRemainder("file:", expression);
        if (remainder != null) {
            if (ObjectHelper.equal(remainder, "name")) {
                return FileExpressionBuilder.fileNameExpression();
            } else if (ObjectHelper.equal(remainder, "name.noext")) {
                return FileExpressionBuilder.fileNameNoExtensionExpression();
            } else if (ObjectHelper.equal(remainder, "parent")) {
                return FileExpressionBuilder.fileParentExpression();
            } else if (ObjectHelper.equal(remainder, "path")) {
                return FileExpressionBuilder.filePathExpression();
            } else if (ObjectHelper.equal(remainder, "absolute.path")) {
                return FileExpressionBuilder.fileAbsolutePathExpression();
            } else if (ObjectHelper.equal(remainder, "canonical.path")) {
                return FileExpressionBuilder.fileCanoicalPathExpression();
            } else if (ObjectHelper.equal(remainder, "length")) {
                return FileExpressionBuilder.fileSizeExpression();
            }
        }

        // date: prefix
        remainder = ifStartsWithReturnRemainder("date:", expression);
        if (remainder != null) {
            String[] parts = remainder.split(":");
            if (parts.length != 2) {
                throw new IllegalSyntaxException(this, expression + " ${date:command:pattern} is the correct syntax.");
            }
            String command = parts[0];
            String pattern = parts[1];
            return FileExpressionBuilder.dateExpression(command, pattern);
        }

        // fallback to simple language if not file specific
        return FileExpressionBuilder.simpleExpression(expression);
    }

}
