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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.ObjectHelper;

public final class GenericFileProcessStrategyFactory {

    private GenericFileProcessStrategyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static GenericFileProcessStrategy createGenericFileProcessStrategy(CamelContext context, Map<String, Object> params) {

        // We assume a value is present only if its value not null for String and 'true' for boolean
        boolean isNoop = params.get("noop") != null;
        boolean isDelete = params.get("delete") != null;
        Expression moveExpression = (Expression) params.get("move");
        Expression preMoveExpression = (Expression) params.get("preMove");

        if (isNoop) {
            GenericFileNoOpProcessStrategy strategy = new GenericFileNoOpProcessStrategy();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        } else if (isDelete) {
            GenericFileDeleteProcessStrategy strategy = new GenericFileDeleteProcessStrategy();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        } else if (moveExpression != null || preMoveExpression != null) {
            FileRenameProcessStrategy strategy = new FileRenameProcessStrategy();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (moveExpression != null) {
                GenericFileExpressionRenamer renamer = new GenericFileExpressionRenamer();
                renamer.setExpression(moveExpression);
                strategy.setCommitRenamer(renamer);
            }
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer renamer = new GenericFileExpressionRenamer();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            return strategy;
        } else {
            // default strategy will do nothing
            GenericFileNoOpProcessStrategy strategy = new GenericFileNoOpProcessStrategy();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        }
    }

    private static GenericFileExclusiveReadLockStrategy getExclusiveReadLockStrategy(Map<String, Object> params) {
        GenericFileExclusiveReadLockStrategy strategy = (GenericFileExclusiveReadLockStrategy) params.get("exclusiveReadLockStrategy");
        if (strategy != null) {
            return strategy;
        }

        // no explicit stategy set then fallback to readLock option
        String readLock = (String) params.get("readLock");
        if (ObjectHelper.isNotEmpty(readLock)) {
            if ("none".equals(readLock) || "false".equals(readLock)) {
                return null;
            } else if ("rename".equals(readLock)) {
                GenericFileRenameExclusiveReadLockStrategy readLockStrategy = new GenericFileRenameExclusiveReadLockStrategy();
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                return readLockStrategy;
            }
        }

        return null;
    }
}
