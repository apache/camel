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

import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.component.file.FileProcessStrategy;

/**
 * Factory to provide the {@link org.apache.camel.component.file.FileProcessStrategy} to use.
 */
public final class FileProcessStrategyFactory {

    private FileProcessStrategyFactory() {
        // Factory class
    }

    /**
     * A strategy method to lazily create the file strategy to use.
     */
    public static FileProcessStrategy createFileProcessStrategy(Map<String, Object> params) {

        // We assume a value is present only if its value not null for String and 'true' for boolean
        boolean isDelete = params.get("delete") != null;
        boolean isLock = params.get("lock") != null;
        String moveNamePrefix = (String) params.get("moveNamePrefix");
        String moveNamePostfix = (String) params.get("moveNamePostfix");
        String preMoveNamePrefix = (String) params.get("preMoveNamePrefix");
        String preMoveNamePostfix = (String) params.get("preMoveNamePostfix");
        Expression expression = (Expression) params.get("expression");
        Expression preMoveExpression = (Expression) params.get("preMoveExpression");
        boolean move = moveNamePrefix != null || moveNamePostfix != null;
        boolean preMove = preMoveNamePrefix != null || preMoveNamePostfix != null;

        if (params.containsKey("noop")) {
            return new NoOpFileProcessStrategy(isLock);
        } else if (move || preMove) {
            RenameFileProcessStrategy strategy = new RenameFileProcessStrategy(isLock);
            if (move) {
                strategy.setCommitRenamer(new DefaultFileRenamer(moveNamePrefix, moveNamePostfix));
            }
            if (preMove) {
                strategy.setBeginRenamer(new DefaultFileRenamer(preMoveNamePrefix, preMoveNamePostfix));
            }
            return strategy;
        } else if (expression != null || preMoveExpression != null) {
            RenameFileProcessStrategy strategy = new RenameFileProcessStrategy(isLock);
            if (expression != null) {
                FileExpressionRenamer renamer = new FileExpressionRenamer();
                renamer.setExpression(expression);
                strategy.setCommitRenamer(renamer);
            }
            if (preMoveExpression != null) {
                FileExpressionRenamer renamer = new FileExpressionRenamer();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            return strategy;
        } else if (isDelete) {
            return new DeleteFileProcessStrategy(isLock);
        } else {
            // default strategy will move to .camel subfolder
            return new RenameFileProcessStrategy(isLock);
        }
    }
}
