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
package org.apache.camel.component.file.remote.strategy;

import java.util.Map;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileDeleteProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileExpressionRenamer;
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileRenameExclusiveReadLockStrategy;
import org.apache.camel.component.file.strategy.GenericFileRenameProcessStrategy;
import org.apache.camel.util.ObjectHelper;

public final class SftpProcessStrategyFactory {

    private SftpProcessStrategyFactory() {
    }

    public static GenericFileProcessStrategy<ChannelSftp.LsEntry> createGenericFileProcessStrategy(CamelContext context, Map<String, Object> params) {

        // We assume a value is present only if its value not null for String
        // and 'true' for boolean
        Expression moveExpression = (Expression)params.get("move");
        Expression moveFailedExpression = (Expression)params.get("moveFailed");
        Expression preMoveExpression = (Expression)params.get("preMove");
        boolean isNoop = params.get("noop") != null;
        boolean isDelete = params.get("delete") != null;
        boolean isMove = moveExpression != null || preMoveExpression != null || moveFailedExpression != null;

        if (isDelete) {
            GenericFileDeleteProcessStrategy<ChannelSftp.LsEntry> strategy = new GenericFileDeleteProcessStrategy<>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer<ChannelSftp.LsEntry> renamer = new GenericFileExpressionRenamer<>();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            if (moveFailedExpression != null) {
                GenericFileExpressionRenamer<ChannelSftp.LsEntry> renamer = new GenericFileExpressionRenamer<>();
                renamer.setExpression(moveFailedExpression);
                strategy.setFailureRenamer(renamer);
            }
            return strategy;
        } else if (isMove || isNoop) {
            GenericFileRenameProcessStrategy<ChannelSftp.LsEntry> strategy = new GenericFileRenameProcessStrategy<>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (!isNoop && moveExpression != null) {
                // move on commit is only possible if not noop
                GenericFileExpressionRenamer<ChannelSftp.LsEntry> renamer = new GenericFileExpressionRenamer<>();
                renamer.setExpression(moveExpression);
                strategy.setCommitRenamer(renamer);
            }
            // both move and noop supports pre move
            if (moveFailedExpression != null) {
                GenericFileExpressionRenamer<ChannelSftp.LsEntry> renamer = new GenericFileExpressionRenamer<>();
                renamer.setExpression(moveFailedExpression);
                strategy.setFailureRenamer(renamer);
            }
            // both move and noop supports pre move
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer<ChannelSftp.LsEntry> renamer = new GenericFileExpressionRenamer<>();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            return strategy;
        } else {
            // default strategy will do nothing
            GenericFileNoOpProcessStrategy<ChannelSftp.LsEntry> strategy = new GenericFileNoOpProcessStrategy<>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            return strategy;
        }
    }

    @SuppressWarnings("unchecked")
    private static GenericFileExclusiveReadLockStrategy<ChannelSftp.LsEntry> getExclusiveReadLockStrategy(Map<String, Object> params) {
        GenericFileExclusiveReadLockStrategy<ChannelSftp.LsEntry> strategy = (GenericFileExclusiveReadLockStrategy<ChannelSftp.LsEntry>)params.get("exclusiveReadLockStrategy");
        if (strategy != null) {
            return strategy;
        }

        // no explicit strategy set then fallback to readLock option
        String readLock = (String)params.get("readLock");
        if (ObjectHelper.isNotEmpty(readLock)) {
            if ("none".equals(readLock) || "false".equals(readLock)) {
                return null;
            } else if ("rename".equals(readLock)) {
                GenericFileRenameExclusiveReadLockStrategy<ChannelSftp.LsEntry> readLockStrategy = new GenericFileRenameExclusiveReadLockStrategy<>();
                Long timeout = (Long)params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                Long checkInterval = (Long)params.get("readLockCheckInterval");
                if (checkInterval != null) {
                    readLockStrategy.setCheckInterval(checkInterval);
                }
                LoggingLevel readLockLoggingLevel = (LoggingLevel)params.get("readLockLoggingLevel");
                if (readLockLoggingLevel != null) {
                    readLockStrategy.setReadLockLoggingLevel(readLockLoggingLevel);
                }
                return readLockStrategy;
            } else if ("changed".equals(readLock)) {
                SftpChangedExclusiveReadLockStrategy readLockStrategy = new SftpChangedExclusiveReadLockStrategy();
                Long timeout = (Long)params.get("readLockTimeout");
                if (timeout != null) {
                    readLockStrategy.setTimeout(timeout);
                }
                Long checkInterval = (Long)params.get("readLockCheckInterval");
                if (checkInterval != null) {
                    readLockStrategy.setCheckInterval(checkInterval);
                }
                Long minLength = (Long)params.get("readLockMinLength");
                if (minLength != null) {
                    readLockStrategy.setMinLength(minLength);
                }
                Long minAge = (Long)params.get("readLockMinAge");
                if (null != minAge) {
                    readLockStrategy.setMinAge(minAge);
                }
                Boolean fastExistsCheck = (Boolean)params.get("fastExistsCheck");
                if (fastExistsCheck != null) {
                    readLockStrategy.setFastExistsCheck(fastExistsCheck);
                }
                LoggingLevel readLockLoggingLevel = (LoggingLevel)params.get("readLockLoggingLevel");
                if (readLockLoggingLevel != null) {
                    readLockStrategy.setReadLockLoggingLevel(readLockLoggingLevel);
                }
                return readLockStrategy;
            }
        }

        return null;
    }

}
