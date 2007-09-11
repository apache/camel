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
package org.apache.camel.component.file;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.strategy.FileProcessStrategy;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @version $Revision: 523016 $
 */
public class FileConsumer extends ScheduledPollConsumer<FileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FileConsumer.class);
    private final FileEndpoint endpoint;
    private boolean recursive = true;
    private String regexPattern = "";
    private long lastPollTime;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void poll() throws Exception {
        pollFileOrDirectory(endpoint.getFile(), isRecursive());
        lastPollTime = System.currentTimeMillis();
    }

    protected void pollFileOrDirectory(File fileOrDirectory, boolean processDir) {
        if (!fileOrDirectory.isDirectory()) {
            pollFile(fileOrDirectory); // process the file
        }
        else if (processDir) {
            if (isValidFile(fileOrDirectory)) {
                LOG.debug("Polling directory " + fileOrDirectory);
                File[] files = fileOrDirectory.listFiles();
                for (int i = 0; i < files.length; i++) {
                    pollFileOrDirectory(files[i], isRecursive()); // self-recursion
                }
            }
        }
        else {
            LOG.debug("Skipping directory " + fileOrDirectory);
        }
    }
    
    ConcurrentHashMap<File, File> filesBeingProcessed = new ConcurrentHashMap<File, File>();

    protected void pollFile(final File file) {
        

        if (!file.exists()) {
            return;
        }
        if (isValidFile(file)) {
            // we only care about file modified times if we are not deleting/moving files
            if (endpoint.isNoop()) {
                long fileModified = file.lastModified();
                if (fileModified <= lastPollTime) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Ignoring file: " + file + " as modified time: " + fileModified + " less than last poll time: " + lastPollTime);
                    }
                    return;
                }
            } else {
                if (filesBeingProcessed.contains(file)) {
                    return;
                }
                filesBeingProcessed.put(file, file);
            }

            final FileProcessStrategy processStrategy = endpoint.getFileStrategy();
            final FileExchange exchange = endpoint.createExchange(file);

            endpoint.configureMessage(file, exchange.getIn());
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("About to process file:  " + file + " using exchange: " + exchange);
                }
                if (processStrategy.begin(endpoint, exchange, file)) {
                    
                    // Use the async processor interface so that processing of
                    // the
                    // exchange can happen asynchronously
                    getAsyncProcessor().process(exchange, new AsyncCallback() {
                        public void done(boolean sync) {
                            if (exchange.getException() == null) {
                                try {
                                    processStrategy.commit(endpoint, (FileExchange)exchange, file);
                                } catch (Exception e) {
                                    handleException(e);
                                }
                            } else {
                                handleException(exchange.getException());
                            }
                            filesBeingProcessed.remove(file);
                        }
                    });
                    
                }
                else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(endpoint + " cannot process file: " + file);
                    }
                }
            }
            catch (Throwable e) {
                handleException(e);
            }
        }
    }

    protected boolean isValidFile(File file) {
        boolean result = false;
        if (file != null && file.exists()) {
            if (isMatched(file)) {
                result = true;
            }
        }
        return result;
    }

    protected boolean isMatched(File file) {
        String name = file.getName();
        if (regexPattern != null && regexPattern.length() > 0) {
            if (!name.matches(getRegexPattern())) {
                return false;
            }
        }
        String[] prefixes = endpoint.getExcludedNamePrefixes();
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    return false;
                }
            }
        }
        String[] postfixes = endpoint.getExcludedNamePostfixes();
        if (postfixes != null) {
            for (String postfix : postfixes) {
                if (name.endsWith(postfix)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return the recursive
     */
    public boolean isRecursive() {
        return this.recursive;
    }

    /**
     * @param recursive the recursive to set
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * @return the regexPattern
     */
    public String getRegexPattern() {
        return this.regexPattern;
    }

    /**
     * @param regexPattern the regexPattern to set
     */
    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

}
