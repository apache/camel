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
package org.apache.camel.component.file.remote;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class RemoteFileConsumer<T extends RemoteFileExchange> extends ScheduledPollConsumer<T> {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected RemoteFileEndpoint<T> endpoint;

    // @deprecated lastPollTime to be removed in Camel 2.0
    protected long lastPollTime;

    protected boolean recursive;
    protected String regexPattern;
    protected boolean setNames = true;
    protected boolean exclusiveReadLock;
    protected boolean deleteFile;
    protected String moveNamePrefix;
    protected String moveNamePostfix;
    protected String excludedNamePrefix;
    protected String excludedNamePostfix;
    private boolean timestamp;

    public RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor,
                              ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
    }

    /**
     * Gets the filename.
     *
     * @param file the file object for the given consumer implementation.
     * @return the filename as String.
     */
    protected abstract String getFileName(Object file);

    /**
     * Is the given file matched to be consumed.
     */
    protected boolean isMatched(Object file) {
        String name = getFileName(file);

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }

        if (regexPattern != null && regexPattern.length() > 0) {
            if (!name.matches(regexPattern)) {
                return false;
            }
        }

        if (excludedNamePrefix != null) {
            if (name.startsWith(excludedNamePrefix)) {
                return false;
            }
        }
        if (excludedNamePostfix != null) {
            if (name.endsWith(excludedNamePostfix)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Should the file be moved after consuming?
     */
    protected boolean isMoveFile() {
        return moveNamePostfix != null || moveNamePrefix != null || endpoint.getConfiguration().getExpression() != null;
    }

    /**
     * Gets the to filename for moving.
     *
     * @param name the original filename
     * @param exchange the current exchange
     * @return the move filename
     */
    protected String getMoveFileName(String name, Exchange exchange) {
        // move according to the expression
        if (endpoint.getConfiguration().getExpression()  != null) {
            Object result = endpoint.getConfiguration().getExpression() .evaluate(exchange);
            return exchange.getContext().getTypeConverter().convertTo(String.class, result);
        }

        // move according to the pre and postfix
        StringBuffer buffer = new StringBuffer();
        if (moveNamePrefix != null) {
            buffer.append(moveNamePrefix);
        }
        buffer.append(name);
        if (moveNamePostfix != null) {
            buffer.append(moveNamePostfix);
        }
        return buffer.toString();
    }

    protected String remoteServer() {
        return endpoint.getConfiguration().remoteServerInformation();
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public long getLastPollTime() {
        return lastPollTime;
    }

    public void setLastPollTime(long lastPollTime) {
        this.lastPollTime = lastPollTime;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public boolean isSetNames() {
        return setNames;
    }

    public void setSetNames(boolean setNames) {
        this.setNames = setNames;
    }

    public boolean isExclusiveReadLock() {
        return exclusiveReadLock;
    }

    public void setExclusiveReadLock(boolean exclusiveReadLock) {
        this.exclusiveReadLock = exclusiveReadLock;
    }

    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }

    public String getMoveNamePrefix() {
        return moveNamePrefix;
    }

    public void setMoveNamePrefix(String moveNamePrefix) {
        this.moveNamePrefix = moveNamePrefix;
    }

    public String getMoveNamePostfix() {
        return moveNamePostfix;
    }

    public void setMoveNamePostfix(String moveNamePostfix) {
        this.moveNamePostfix = moveNamePostfix;
    }

    public String getExcludedNamePrefix() {
        return excludedNamePrefix;
    }

    public void setExcludedNamePrefix(String excludedNamePrefix) {
        this.excludedNamePrefix = excludedNamePrefix;
    }

    public String getExcludedNamePostfix() {
        return excludedNamePostfix;
    }

    public void setExcludedNamePostfix(String excludedNamePostfix) {
        this.excludedNamePostfix = excludedNamePostfix;
    }

    /**
     * @deprecated the timestamp feature will be removed in Camel 2.0
     */
    public boolean isTimestamp() {
        return timestamp;
    }

    /**
     * Sets wether polling should use last poll timestamp for filtering only new files.
     * @deprecated the timestamp feature will be removed in Camel 2.0
     */
    public void setTimestamp(boolean timestamp) {
        this.timestamp = timestamp;
    }
}
