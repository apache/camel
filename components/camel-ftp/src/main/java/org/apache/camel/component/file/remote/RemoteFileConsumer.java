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

import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class RemoteFileConsumer<T extends RemoteFileExchange> extends ScheduledPollConsumer<T> {
    protected final transient Log LOG = LogFactory.getLog(getClass());
    protected RemoteFileEndpoint<T> endpoint;
    protected long lastPollTime;

    protected boolean recursive = true;
    protected String regexPattern;
    protected boolean setNames = true;
    protected boolean exclusiveRead = true;
    protected boolean deleteFile;
    protected String moveNamePrefix;
    protected String moveNamePostfix;

    public RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public RemoteFileConsumer(RemoteFileEndpoint<T> endpoint, Processor processor, ScheduledExecutorService executor) {
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
     * Is the given file matched to be consumed (will consider regexp if provided as an option).
     * <p/>
     * Note: Returns true if no reg exp is used.
     */
    protected boolean isMatched(Object file) {
        String fileName = getFileName(file);

        boolean result = true;
        if (regexPattern != null && regexPattern.length() > 0) {
            result = fileName.matches(regexPattern);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Matching file: " + fileName + " is " + result);
        }
        return result;
    }

    /**
     * Should the file be moved after consuming?
     */
    protected boolean isMoveFile() {
        return moveNamePostfix != null || moveNamePrefix != null;
    }

    /**
     * Gets the to filename for moving.
     * 
     * @param name the original filename
     * @return the move filename
     */
    protected String getMoveFileName(String name) {
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

    public boolean isExclusiveRead() {
        return exclusiveRead;
    }

    public void setExclusiveRead(boolean exclusiveRead) {
        this.exclusiveRead = exclusiveRead;
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

}
