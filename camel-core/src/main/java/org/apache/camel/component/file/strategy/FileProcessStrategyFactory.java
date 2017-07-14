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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;

public final class FileProcessStrategyFactory {

    private FileProcessStrategyFactory() {
    }

    public static GenericFileProcessStrategy<File> createGenericFileProcessStrategy(CamelContext context, Map<String, Object> params) {

        // We assume a value is present only if its value not null for String and 'true' for boolean
        Expression moveExpression = (Expression) params.get("move");
        Expression moveFailedExpression = (Expression) params.get("moveFailed");
        Expression preMoveExpression = (Expression) params.get("preMove");
        boolean isNoop = params.get("noop") != null;
        boolean isDelete = params.get("delete") != null;
        boolean isMove = moveExpression != null || preMoveExpression != null || moveFailedExpression != null;

        if (isDelete) {
            GenericFileDeleteProcessStrategy<File> strategy = new GenericFileDeleteProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            if (moveFailedExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(moveFailedExpression);
                strategy.setFailureRenamer(renamer);
            }
            return strategy;
        } else if (isMove || isNoop) {
            GenericFileRenameProcessStrategy<File> strategy = new GenericFileRenameProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            if (!isNoop) {
                // move on commit is only possible if not noop
                if (moveExpression != null) {
                    GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                    renamer.setExpression(moveExpression);
                    strategy.setCommitRenamer(renamer);
                } else {
                    strategy.setCommitRenamer(getDefaultCommitRenamer(context));
                }
            }
            // both move and noop supports pre move
            if (preMoveExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(preMoveExpression);
                strategy.setBeginRenamer(renamer);
            }
            // both move and noop supports move failed
            if (moveFailedExpression != null) {
                GenericFileExpressionRenamer<File> renamer = new GenericFileExpressionRenamer<File>();
                renamer.setExpression(moveFailedExpression);
                strategy.setFailureRenamer(renamer);
            }
            return strategy;
        } else {
            // default strategy will move files in a .camel/ subfolder where the file was consumed
            GenericFileRenameProcessStrategy<File> strategy = new GenericFileRenameProcessStrategy<File>();
            strategy.setExclusiveReadLockStrategy(getExclusiveReadLockStrategy(params));
            strategy.setCommitRenamer(getDefaultCommitRenamer(context));
            return strategy;
        }
    }

    private static GenericFileExpressionRenamer<File> getDefaultCommitRenamer(CamelContext context) {
        // use context to lookup language to let it be loose coupled
        Language language = context.resolveLanguage("file");
        Expression expression = language.createExpression("${file:parent}/.camel/${file:onlyname}");
        return new GenericFileExpressionRenamer<File>(expression);
    }

    @SuppressWarnings("unchecked")
    private static GenericFileExclusiveReadLockStrategy<File> getExclusiveReadLockStrategy(Map<String, Object> params) {
        GenericFileExclusiveReadLockStrategy<File> strategy = (GenericFileExclusiveReadLockStrategy<File>) params.get("exclusiveReadLockStrategy");
        if (strategy != null) {
            return strategy;
        }

        // no explicit strategy set then fallback to readLock option
        String readLock = (String) params.get("readLock");
        if (ObjectHelper.isNotEmpty(readLock)) {
            if ("none".equals(readLock) || "false".equals(readLock)) {
                return null;
            } else if ("markerFile".equals(readLock)) {
                strategy = new MarkerFileExclusiveReadLockStrategy();
            } else if ("fileLock".equals(readLock)) {
                strategy = new FileLockExclusiveReadLockStrategy();
            } else if ("rename".equals(readLock)) {
                strategy = new FileRenameExclusiveReadLockStrategy();
            } else if ("changed".equals(readLock)) {
                FileChangedExclusiveReadLockStrategy readLockStrategy = new FileChangedExclusiveReadLockStrategy();
                Long minLength = (Long) params.get("readLockMinLength");
                if (minLength != null) {
                    readLockStrategy.setMinLength(minLength);
                }
                Long minAge = (Long) params.get("readLockMinAge");
                if (null != minAge) {
                    readLockStrategy.setMinAge(minAge);
                }
                strategy = readLockStrategy;
            } else if ("idempotent".equals(readLock)) {
                FileIdempotentRepositoryReadLockStrategy readLockStrategy = new FileIdempotentRepositoryReadLockStrategy();
                Boolean readLockRemoveOnRollback = (Boolean) params.get("readLockRemoveOnRollback");
                if (readLockRemoveOnRollback != null) {
                    readLockStrategy.setRemoveOnRollback(readLockRemoveOnRollback);
                }
                Boolean readLockRemoveOnCommit = (Boolean) params.get("readLockRemoveOnCommit");
                if (readLockRemoveOnCommit != null) {
                    readLockStrategy.setRemoveOnCommit(readLockRemoveOnCommit);
                }
                IdempotentRepository repo = (IdempotentRepository) params.get("readLockIdempotentRepository");
                if (repo != null) {
                    readLockStrategy.setIdempotentRepository(repo);
                }
                strategy = readLockStrategy;
            } else if ("idempotent-changed".equals(readLock)) {
                FileIdempotentChangedRepositoryReadLockStrategy readLockStrategy = new FileIdempotentChangedRepositoryReadLockStrategy();
                Boolean readLockRemoveOnRollback = (Boolean) params.get("readLockRemoveOnRollback");
                if (readLockRemoveOnRollback != null) {
                    readLockStrategy.setRemoveOnRollback(readLockRemoveOnRollback);
                }
                Boolean readLockRemoveOnCommit = (Boolean) params.get("readLockRemoveOnCommit");
                if (readLockRemoveOnCommit != null) {
                    readLockStrategy.setRemoveOnCommit(readLockRemoveOnCommit);
                }
                IdempotentRepository repo = (IdempotentRepository) params.get("readLockIdempotentRepository");
                if (repo != null) {
                    readLockStrategy.setIdempotentRepository(repo);
                }
                Long minLength = (Long) params.get("readLockMinLength");
                if (minLength != null) {
                    readLockStrategy.setMinLength(minLength);
                }
                Long minAge = (Long) params.get("readLockMinAge");
                if (null != minAge) {
                    readLockStrategy.setMinAge(minAge);
                }
                strategy = readLockStrategy;
            } else if ("idempotent-rename".equals(readLock)) {
                FileIdempotentRenameRepositoryReadLockStrategy readLockStrategy = new FileIdempotentRenameRepositoryReadLockStrategy();
                Boolean readLockRemoveOnRollback = (Boolean) params.get("readLockRemoveOnRollback");
                if (readLockRemoveOnRollback != null) {
                    readLockStrategy.setRemoveOnRollback(readLockRemoveOnRollback);
                }
                Boolean readLockRemoveOnCommit = (Boolean) params.get("readLockRemoveOnCommit");
                if (readLockRemoveOnCommit != null) {
                    readLockStrategy.setRemoveOnCommit(readLockRemoveOnCommit);
                }
                IdempotentRepository repo = (IdempotentRepository) params.get("readLockIdempotentRepository");
                if (repo != null) {
                    readLockStrategy.setIdempotentRepository(repo);
                }
                strategy = readLockStrategy;
            }

            if (strategy != null) {
                Long timeout = (Long) params.get("readLockTimeout");
                if (timeout != null) {
                    strategy.setTimeout(timeout);
                }
                Long checkInterval = (Long) params.get("readLockCheckInterval");
                if (checkInterval != null) {
                    strategy.setCheckInterval(checkInterval);
                }
                LoggingLevel readLockLoggingLevel = (LoggingLevel) params.get("readLockLoggingLevel");
                if (readLockLoggingLevel != null) {
                    strategy.setReadLockLoggingLevel(readLockLoggingLevel);
                }
                Boolean readLockMarkerFile = (Boolean) params.get("readLockMarkerFile");
                if (readLockMarkerFile != null) {
                    strategy.setMarkerFiler(readLockMarkerFile);
                }
                Boolean readLockDeleteOrphanLockFiles = (Boolean) params.get("readLockDeleteOrphanLockFiles");
                if (readLockDeleteOrphanLockFiles != null) {
                    strategy.setDeleteOrphanLockFiles(readLockDeleteOrphanLockFiles);
                }
            }
        }

        return strategy;
    }
}
