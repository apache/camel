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
package org.apache.camel.dataformat.tarfile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Iterator which can go through the TarArchiveInputStream according to TarArchiveEntry
 * Based on ZipIterator from camel-zipfile component
 */
public class TarIterator implements Iterator<Message>, Closeable {

    /**
     * Header where this {@link TarIterator} will insert the current entry's file name.
     */
    public static final String TARFILE_ENTRY_NAME_HEADER = "CamelTarFileEntryName";

    private static final Logger LOGGER = LoggerFactory.getLogger(TarIterator.class);

    private final Exchange exchange;
    private volatile TarArchiveInputStream tarInputStream;
    private volatile Message parent;
    private boolean allowEmptyDirectory;

    public TarIterator(Exchange exchange, InputStream inputStream) {
        this.exchange = exchange;
        this.allowEmptyDirectory = false;
        if (inputStream instanceof TarArchiveInputStream) {
            tarInputStream = (TarArchiveInputStream) inputStream;
        } else {
            try {
                ArchiveInputStream input = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, new BufferedInputStream(inputStream));
                tarInputStream = (TarArchiveInputStream) input;
            } catch (ArchiveException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        parent = null;
    }

    @Override
    public boolean hasNext() {
        try {
            if (tarInputStream == null) {
                return false;
            }
            boolean availableDataInCurrentEntry = tarInputStream.available() > 0;
            if (!availableDataInCurrentEntry) {
                // advance to the next entry.
                parent = getNextElement();
                if (parent == null) {
                    tarInputStream.close();
                    availableDataInCurrentEntry = false;
                } else {
                    availableDataInCurrentEntry = true;
                }
            }
            return availableDataInCurrentEntry;
        } catch (IOException exception) {
            //Just wrap the IOException as CamelRuntimeException
            throw new RuntimeCamelException(exception);
        }
    }

    @Override
    public Message next() {
        if (parent == null) {
            parent = getNextElement();
        }
        Message answer = parent;
        parent = null;
        checkNullAnswer(answer);

        return answer;
    }

    private Message getNextElement() {
        if (tarInputStream == null) {
            return null;
        }

        try {
            TarArchiveEntry current = getNextEntry();

            if (current != null) {
                LOGGER.debug("Reading tarEntry {}", current.getName());
                Message answer = new DefaultMessage(exchange.getContext());
                answer.getHeaders().putAll(exchange.getIn().getHeaders());
                answer.setHeader(TARFILE_ENTRY_NAME_HEADER, current.getName());
                answer.setHeader(Exchange.FILE_NAME, current.getName());
                if (current.getSize() > 0) {
                    answer.setBody(new TarElementInputStreamWrapper(tarInputStream));
                } else {
                    // Workaround for the case when the entry is zero bytes big
                    answer.setBody(new ByteArrayInputStream(new byte[0]));
                }
                return answer;
            } else {
                LOGGER.trace("Closed tarInputStream");
                return null;
            }
        } catch (IOException exception) {
            //Just wrap the IOException as CamelRuntimeException
            throw new RuntimeCamelException(exception);
        }
    }

    public void checkNullAnswer(Message answer) {
        if (answer == null && tarInputStream != null) {
            IOHelper.close(tarInputStream);
            tarInputStream = null;
        }
    }

    private TarArchiveEntry getNextEntry() throws IOException {
        TarArchiveEntry entry;

        while ((entry = tarInputStream.getNextTarEntry()) != null) {
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
        IOHelper.close(tarInputStream);
        tarInputStream = null;
    }

    public boolean isAllowEmptyDirectory() {
        return allowEmptyDirectory;
    }

    public void setAllowEmptyDirectory(boolean allowEmptyDirectory) {
        this.allowEmptyDirectory = allowEmptyDirectory;
    }
}