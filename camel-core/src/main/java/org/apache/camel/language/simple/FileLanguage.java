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
import org.apache.camel.builder.FileExpressionBuilder;
import org.apache.camel.language.IllegalSyntaxException;
import org.apache.camel.util.ObjectHelper;

/**
 * File language is an extension to Simple language to add file specific expressions.
 *
 * Examples of supported file expressions are:
 * <ul>
 *   <li><tt>file:name</tt> to access the file name (is relative, see note below))</li>
 *   <li><tt>file:name.noext</tt> to access the file name with no extension</li>
 *   <li><tt>file:ext</tt> to access the file extension</li>
 *   <li><tt>file:onlyname</tt> to access the file name (no paths)</li>
 *   <li><tt>file:onlyname.noext</tt> to access the file name (no paths) with no extension </li>
 *   <li><tt>file:parent</tt> to access the parent file name</li>
 *   <li><tt>file:path</tt> to access the file path name</li>
 *   <li><tt>file:absolute</tt> is the file regarded as absolute or relative</li>
 *   <li><tt>file:absolute.path</tt> to access the absolute file path name</li>
 *   <li><tt>file:length</tt> to access the file length as a Long type</li>
 *   <li><tt>file:modified</tt> to access the file last modified as a Date type</li>
 *   <li><tt>date:&lt;command&gt;:&lt;pattern&gt;</tt> for date formatting using the {@link java.text.SimpleDateFormat} patterns.
 *     Additional Supported commands are: <tt>file</tt> for the last modified timestamp of the file.
 *     All the commands from {@link SimpleLanguage} is also avaiable.
 *   </li>
 * </ul>
 * The <b>relative</b> file is the filename with the starting directory clipped, as opposed to <b>path</b> that will
 * return the full path including the starting directory.
 * <br/>
 * The <b>only</b> file is the filename only with all paths clipped.
 * <br/>
  * All the simple expression is also available so you can eg use <tt>${in.header.foo}</tt> to access the foo header.
 *
 * @see org.apache.camel.language.simple.SimpleLanguage
 * @see org.apache.camel.language.bean.BeanLanguage
 */
public class FileLanguage extends SimpleLanguageSupport {

    public static Expression file(String expression) {
        FileLanguage language = new FileLanguage();
        return language.createExpression(expression);
    }

    protected Expression createSimpleExpression(String expression) {

        // file: prefix
        String remainder = ifStartsWithReturnRemainder("file:", expression);
        if (remainder != null) {
            if (ObjectHelper.equal(remainder, "name")) {
                return FileExpressionBuilder.fileNameExpression();
            } else if (ObjectHelper.equal(remainder, "name.noext")) {
                return FileExpressionBuilder.fileNameNoExtensionExpression();
            } else if (ObjectHelper.equal(remainder, "onlyname")) {
                return FileExpressionBuilder.fileOnlyNameExpression();
            } else if (ObjectHelper.equal(remainder, "onlyname.noext")) {
                return FileExpressionBuilder.fileOnlyNameNoExtensionExpression();
            } else if (ObjectHelper.equal(remainder, "ext")) {
                return FileExpressionBuilder.fileExtensionExpression();
            } else if (ObjectHelper.equal(remainder, "parent")) {
                return FileExpressionBuilder.fileParentExpression();
            } else if (ObjectHelper.equal(remainder, "path")) {
                return FileExpressionBuilder.filePathExpression();
            } else if (ObjectHelper.equal(remainder, "absolute")) {
                return FileExpressionBuilder.fileAbsoluteExpression();
            } else if (ObjectHelper.equal(remainder, "absolute.path")) {
                return FileExpressionBuilder.fileAbsolutePathExpression();
            } else if (ObjectHelper.equal(remainder, "length")) {
                return FileExpressionBuilder.fileSizeExpression();
            } else if (ObjectHelper.equal(remainder, "modified")) {
                return FileExpressionBuilder.fileLastModifiedExpression();
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
