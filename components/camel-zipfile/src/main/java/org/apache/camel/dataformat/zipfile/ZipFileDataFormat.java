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

import static org.apache.camel.Exchange.FILE_LENGTH;
import static org.apache.camel.Exchange.FILE_NAME;

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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/**
 * Zip file data format.
 */
@Dataformat("zipFile")
public class ZipFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    /**
     * The default maximum decompressed size (in bytes), which corresponds to 1G.
     */
    private static final long DEFAULT_MAXIMUM_DECOMPRESSED_SIZE = 1073741824;

    private boolean usingIterator;
    private boolean allowEmptyDirectory;
    private boolean preservePathElements;
    private long maxDecompressedSize = DEFAULT_MAXIMUM_DECOMPRESSED_SIZE;

    @Override
    public String getDataFormatName() {
        return "zipFile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename = null;

        String filepath = exchange.getIn().getHeader(FILE_NAME, String.class);
        Long fileLength = exchange.getIn().getHeader(FILE_LENGTH, Long.class);
        if (filepath != null) {
            Path filenamePath = Paths.get(filepath).getFileName();
            if (filenamePath != null) {
                filename = filenamePath.toString(); // remove any path elements
            }
        }
        if (filename == null) {
            // generate the file name as the camel file component would do
            filename = filepath = StringHelper.sanitize(exchange.getIn().getMessageId());
        }
        InputStream is =
                exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);
        if (fileLength == null) {
            fileLength = (long) is.available();
        }

        ZipArchiveOutputStream zos = new ZipArchiveOutputStream(stream);
        if (preservePathElements) {
            createZipEntries(zos, filepath, fileLength);
        } else {
            createZipEntries(zos, filename, fileLength);
        }

        try {
            IOHelper.copy(is, zos);
        } finally {
            zos.closeArchiveEntry();
            IOHelper.close(is, zos);
        }

        String newFilename = filename + ".zip";
        exchange.getMessage().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream inputStream) throws Exception {
        if (usingIterator) {
            ZipIterator zipIterator = new ZipIterator(exchange, inputStream);
            zipIterator.setAllowEmptyDirectory(allowEmptyDirectory);
            return zipIterator;
        } else {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            ZipArchiveInputStream zis =
                    new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, bis);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                ZipArchiveEntry entry = zis.getNextEntry();
                if (entry != null) {
                    exchange.getMessage().setHeader(FILE_NAME, entry.getName());
                    IOHelper.copy(zis, osb, IOHelper.DEFAULT_BUFFER_SIZE, false, maxDecompressedSize);
                } else {
                    throw new IllegalStateException("Unable to unzip the file, it may be corrupted.");
                }

                entry = zis.getNextEntry();
                if (entry != null) {
                    throw new IllegalStateException("Zip file has more than 1 entry.");
                }

                return osb.build();
            } finally {
                IOHelper.close(osb, zis, bis);
            }
        }
    }

    private void createZipEntries(ZipArchiveOutputStream zos, String filepath, Long fileLength) throws IOException {
        Iterator<Path> elements = Paths.get(filepath).iterator();
        StringBuilder sb = new StringBuilder(256);

        while (elements.hasNext()) {
            Path path = elements.next();
            String element = path.toString();
            Long length = fileLength;

            // If there are more elements to come this element is a directory
            // The "/" at the end tells the ZipEntry it is a folder
            if (elements.hasNext()) {
                element += "/";
                length = 0L;
            }

            // Each entry needs the complete path, including previous created folders.
            ZipArchiveEntry entry = new ZipArchiveEntry(sb + element);
            entry.setSize(length);
            zos.putArchiveEntry(entry);

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

    public long getMaxDecompressedSize() {
        return maxDecompressedSize;
    }

    public void setMaxDecompressedSize(long maxDecompressedSize) {
        this.maxDecompressedSize = maxDecompressedSize;
    }
}
