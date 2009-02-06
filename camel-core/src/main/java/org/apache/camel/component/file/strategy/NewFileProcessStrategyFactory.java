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
import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.ObjectHelper;

public final class NewFileProcessStrategyFactory {

    private NewFileProcessStrategyFactory() {
    }

    public static GenericFileProcessStrategy createGenericFileProcessStrategy(Map<String, Object> params) {

        // We assume a value is present only if its value not null for String and 'true' for boolean
        boolean isNoop = params.get("noop") != null;
        boolean isDelete = params.get("delete") != null;
        String moveNamePrefix = (String) params.get("moveNamePrefix");
        String moveNamePostfix = (String) params.get("moveNamePostfix");
        String preMoveNamePrefix = (String) params.get("preMoveNamePrefix");
        String preMoveNamePostfix = (String) params.get("preMoveNamePostfix");
        Expression moveExpression = (Expression) params.get("moveExpression");
        Expression preMoveExpression = (Expression) params.get("preMoveExpression");
        boolean move = moveNamePrefix != null || moveNamePostfix != null;
        boolean preMove = preMoveNamePrefix != null || preMoveNamePostfix != null;

        if (isNoop) {
            GenericFileNoOpProcessStrategy<File> strategy = new GenericFileNoOpProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        } else if (isDelete) {
            GenericFileDeleteProcessStrategy<File> strategy = new GenericFileDeleteProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        } else if (move || preMove) {
            GenericFileRenameProcessStrategy<File> strategy = new GenericFileRenameProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (move) {
                strategy.setCommitRenamer(new GenericFileDefaultRenamer<File>(moveNamePrefix, moveNamePostfix));
            }
            if (preMove) {
                strategy.setBeginRenamer(new GenericFileDefaultRenamer<File>(preMoveNamePrefix, preMoveNamePostfix));
            }
            return strategy;
        } else if (moveExpression != null || preMoveExpression != null) {
            GenericFileRenameProcessStrategy<File> strategy = new GenericFileRenameProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (moveExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(moveExpression);
                strategy.setCommitRenamer(renamer);
            }
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            return strategy;
        } else {
            // default strategy will move files in a .camel/ subfolder
            GenericFileRenameProcessStrategy<File> strategy = new GenericFileRenameProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            strategy.setCommitRenamer(new GenericFileDefaultRenamer<File>(".camel/", ""));
            return strategy;
        }
    }

    @SuppressWarnings("unchecked")
    private static GenericFileExclusiveReadLockStrategy<File> getExclusiveReadLockStrategy(Map<String, Object> params) {
        GenericFileExclusiveReadLockStrategy strategy = (GenericFileExclusiveReadLockStrategy) params.get("exclusiveReadLockStrategy");
        if (strategy != null) {
            return strategy;
        }

        // no explicit stategy set then fallback to readLock option
        String readLock = (String) params.get("readLock");
        if (ObjectHelper.isNotEmpty(readLock)) {
            if ("none".equals(readLock) || "false".equals(readLock)) {
                return null;
            } else if ("fileLock".equals(readLock)) {
                GenericFileExclusiveReadLockStrategy<File> readLockStrategy = new NewFileLockExclusiveReadLockStrategy();
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                return readLockStrategy;
            } else if ("rename".equals(readLock)) {
                GenericFileExclusiveReadLockStrategy<File> readLockStrategy = new GenericFileRenameExclusiveReadLockStrategy<File>();
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                return readLockStrategy;
            } else if ("markerFile".equals(readLock)) {
                return new NewMarkerFileExclusiveReadLockStrategy();
            }
        }

        return null;
    }
}
