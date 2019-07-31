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
package org.apache.camel.component.scp;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;

public final class ScpProcessStrategyFactory {

    private ScpProcessStrategyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <LsEntry> GenericFileProcessStrategy<LsEntry> createGenericFileProcessStrategy(CamelContext context, Map<String, Object> params) {

        /*
        // We assume a value is present only if its value not null for String and 'true' for boolean
        Expression moveExpression = (Expression) params.get("move");
        Expression moveFailedExpression = (Expression) params.get("moveFailed");
        Expression preMoveExpression = (Expression) params.get("preMove");
        boolean isNoop = params.get("noop") != null;
        boolean isDelete = params.get("delete") != null;
        boolean isMove = moveExpression != null || preMoveExpression != null || moveFailedExpression != null;
        */

        // default strategy will do nothing
        GenericFileNoOpProcessStrategy<LsEntry> strategy = new GenericFileNoOpProcessStrategy<>();
        strategy.setExclusiveReadLockStrategy((GenericFileExclusiveReadLockStrategy<LsEntry>) getExclusiveReadLockStrategy(params));
        return strategy;
    }

    @SuppressWarnings({"unchecked"})
    private static <LsEntry> GenericFileExclusiveReadLockStrategy<LsEntry> getExclusiveReadLockStrategy(Map<String, Object> params) {
        GenericFileExclusiveReadLockStrategy<LsEntry> strategy = (GenericFileExclusiveReadLockStrategy<LsEntry>) params.get("exclusiveReadLockStrategy");
        if (strategy != null) {
            return strategy;
        }
/*
        // no explicit strategy set then fallback to readLock option
        String readLock = (String) params.get("readLock");
        if (ObjectHelper.isNotEmpty(readLock)) {
            if ("none".equals(readLock) || "false".equals(readLock)) {
                return null;
            } else if ("rename".equals(readLock)) {
                GenericFileRenameExclusiveReadLockStrategy<LsEntry> readLockStrategy = new GenericFileRenameExclusiveReadLockStrategy<LsEntry>();
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                return readLockStrategy;
            } else if ("changed".equals(readLock)) {
                GenericFileExclusiveReadLockStrategy readLockStrategy = new SftpChangedExclusiveReadLockStrategy();
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                Long checkInterval = (Long) params.get("readLockCheckInterval");
                if (checkInterval != null) {
                    readLockStrategy.setCheckInterval(checkInterval);
                }
                return readLockStrategy;
            }
        }
*/
        return null;
    }
}
