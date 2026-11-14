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
package org.apache.camel.component.file.strategy;

import java.nio.file.Path;

import org.apache.camel.Expression;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.component.file.GenericFile;

/**
 * The renamer of the default move, into the .camel folder next to the file. The consumer checks where a file would be
 * moved before it processes the file, and for this renamer it can tell without evaluating the expression.
 */
final class DefaultMoveRenamer<T> extends GenericFileExpressionRenamer<T> {

    DefaultMoveRenamer(Expression expression) {
        super(expression);
    }

    /**
     * The path the file is moved to: ${file:parent}/.camel/${file:onlyname}
     */
    static String target(GenericFile<?> file) {
        Path path = Path.of(file.getAbsoluteFilePath());
        Path folder = path.getParent() != null
                ? path.getParent().resolve(FileConstants.DEFAULT_SUB_FOLDER) : Path.of(FileConstants.DEFAULT_SUB_FOLDER);
        return folder.resolve(file.getFileNameOnly()).toString();
    }
}
