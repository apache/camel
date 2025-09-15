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
package org.apache.camel.dataformat.zipfile;

import java.io.*;
import java.util.Iterator;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Iterator which can go through the ZipInputStream according to ZipEntry Based on the thread
 * <a href="http://camel.465427.n5.nabble.com/zip-file-best-practices-td5713437.html">zip file best practices</a>
 */
public class ZipIterator implements Iterator<Message>, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ZipIterator.class);

    private final Exchange exchange;
    private boolean allowEmptyDirectory;
    private volatile ZipArchiveInputStream zipInputStream;
    private volatile ZipArchiveEntry currentEntry;
    private volatile Message parent;
    private volatile boolean first;

    public ZipIterator(Exchange exchange, InputStream inputStream) {
        this.exchange = exchange;
        this.allowEmptyDirectory = false;

        Objects.requireNonNull(inputStream);

        if (inputStream instanceof ZipArchiveInputStream) {
            zipInputStream = (ZipArchiveInputStream) inputStream;
        } else {
            try {
                ArchiveInputStream input = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP,
                        new BufferedInputStream(inputStream));
                zipInputStream = (ZipArchiveInputStream) input;
            } catch (ArchiveException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        parent = null;
        first = true;
    }

    @Override
    public boolean hasNext() {
        boolean answer = doHasNext();
        LOG.trace("hasNext: {}", answer);
        return answer;
    }

    protected boolean doHasNext() {
        try {
            if (zipInputStream == null) {
                return false;
            }
            boolean availableDataInCurrentEntry = currentEntry != null;
            if (!availableDataInCurrentEntry) {
                // advance to the next entry.
                parent = getNextElement();
                if (parent == null) {
                    zipInputStream.close();
                } else {
                    availableDataInCurrentEntry = true;
                }
            }
            return availableDataInCurrentEntry;
        } catch (IOException exception) {
            throw new RuntimeCamelException(exception);
        }
    }

    @Override
    public Message next() {
        Message answer = doNext();
        LOG.trace("next: {}", answer);
        return answer;
    }

    protected Message doNext() {
        if (parent == null) {
            parent = getNextElement();
        }
        Message answer = parent;
        parent = null;
        currentEntry = null;

        if (first && answer == null) {
            throw new IllegalStateException("Unable to unzip the file, it may be corrupted.");
        }

        first = false;
        checkNullAnswer(answer);

        return answer;
    }

    private Message getNextElement() {
        if (zipInputStream == null) {
            return null;
        }

        try {
            currentEntry = getNextEntry();

            if (currentEntry != null) {
                LOG.debug("read zipEntry {}", currentEntry.getName());

                Message answer = new DefaultMessage(exchange.getContext());
                answer.getHeaders().putAll(exchange.getIn().getHeaders());
                answer.setHeader("zipFileName", currentEntry.getName());
                answer.setHeader(Exchange.FILE_NAME, currentEntry.getName());
                if (currentEntry.isDirectory()) {
                    if (allowEmptyDirectory) {
                        answer.setBody(new ByteArrayInputStream(new byte[0]));
                    } else {
                        return getNextElement(); // skip directory
                    }
                } else {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    IOHelper.copy(zipInputStream, baos);
                    byte[] data = baos.toByteArray();
                    answer.setBody(new ByteArrayInputStream(data));
                }

                return answer;
            } else {
                LOG.trace("close zipInputStream");
                return null;
            }
        } catch (IOException exception) {
            throw new RuntimeCamelException(exception);
        }
    }

    public void checkNullAnswer(Message answer) {
        if (answer == null && zipInputStream != null) {
            IOHelper.close(zipInputStream);
            zipInputStream = null;
        }
    }

    private ZipArchiveEntry getNextEntry() throws IOException {
        ZipArchiveEntry entry;

        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return entry;
            } else {
                if (allowEmptyDirectory) {
                    return entry;
                }
            }
        }

        return null;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        IOHelper.close(zipInputStream);
        zipInputStream = null;
        currentEntry = null;
    }

    public boolean isSupportIteratorForEmptyDirectory() {
        return allowEmptyDirectory;
    }

    public void setAllowEmptyDirectory(boolean allowEmptyDirectory) {
        this.allowEmptyDirectory = allowEmptyDirectory;
    }
}
