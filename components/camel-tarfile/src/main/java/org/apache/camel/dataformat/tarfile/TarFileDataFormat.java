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
package org.apache.camel.dataformat.tarfile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import static org.apache.camel.Exchange.FILE_LENGTH;
import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Tar file data format.
 * Based on ZipFileDataFormat from camel-zipfile component
 */
@Dataformat("tarfile")
public class TarFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private boolean usingIterator;
    private boolean allowEmptyDirectory;
    private boolean preservePathElements;

    @Override
    public String getDataFormatName() {
        return "tarfile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename;
        String filepath = exchange.getIn().getHeader(FILE_NAME, String.class);
        Long filelength = exchange.getIn().getHeader(FILE_LENGTH, Long.class);
        if (filepath == null) {
            // generate the file name as the camel file component would do
            filename = filepath = StringHelper.sanitize(exchange.getIn().getMessageId());
        } else {
            filename = Paths.get(filepath).getFileName().toString(); // remove any path elements
        }

        TarArchiveOutputStream tos = new TarArchiveOutputStream(stream);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, graph);
        if (filelength == null) {
            filelength = (long) is.available();
        }

        if (preservePathElements) {
            createTarEntries(tos, filepath, filelength);
        } else {
            createTarEntries(tos, filename, filelength);
        }

        try {
            IOHelper.copy(is, tos);
        } finally {
            tos.closeArchiveEntry();
            IOHelper.close(is, tos);
        }

        String newFilename = filename + ".tar";
        exchange.getMessage().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        if (usingIterator) {
            TarIterator tarIterator = new TarIterator(exchange, stream);
            tarIterator.setAllowEmptyDirectory(allowEmptyDirectory);
            return tarIterator;
        } else {
            BufferedInputStream bis = new BufferedInputStream(stream);
            TarArchiveInputStream tis = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, bis);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                TarArchiveEntry entry = tis.getNextTarEntry();
                if (entry != null) {
                    exchange.getMessage().setHeader(FILE_NAME, entry.getName());
                    IOHelper.copy(tis, osb);
                } else {
                    throw new IllegalStateException("Unable to untar the file, it may be corrupted.");
                }

                entry = tis.getNextTarEntry();
                if (entry != null) {
                    throw new IllegalStateException("Tar file has more than 1 entry.");
                }

                return osb.build();

            } finally {
                IOHelper.close(osb, tis, bis);
            }
        }
    }

    private void createTarEntries(TarArchiveOutputStream tos, String filepath, Long filelength) throws IOException {
        Iterator<Path> elements = Paths.get(filepath).iterator();
        StringBuilder sb = new StringBuilder();

        while (elements.hasNext()) {
            Path path = elements.next();
            String element = path.toString();
            Long length = filelength;

            // If there are more elements to come this element is a directory
            // The "/" at the end tells the TarEntry it is a folder
            if (elements.hasNext()) {
                element += "/";
                length = 0L;
            }

            // Each entry needs the complete path, including previous created folders.
            TarArchiveEntry entry = new TarArchiveEntry(sb + element);
            entry.setSize(length);
            tos.putArchiveEntry(entry);

            sb.append(element);
        }
    }

    public boolean isUsingIterator() {
        return usingIterator;
    }

    public void setUsingIterator(boolean usingIterator) {
        this.usingIterator = usingIterator;
    }

    public boolean isAllowEmptyDirectory() {
        return allowEmptyDirectory;
    }

    public void setAllowEmptyDirectory(boolean allowEmptyDirectory) {
        this.allowEmptyDirectory = allowEmptyDirectory;
    }

    public boolean isPreservePathElements() {
        return preservePathElements;
    }

    public void setPreservePathElements(boolean preservePathElements) {
        this.preservePathElements = preservePathElements;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
