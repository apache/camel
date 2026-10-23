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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import static org.apache.camel.Exchange.FILE_NAME;

/**
 * Tar file data format. Based on ZipFileDataFormat from camel-zipfile component
 */
@Dataformat("tarFile")
public class TarFileDataFormat extends ServiceSupport implements DataFormat, DataFormatName {
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
        return "tarFile";
    }

    @Override
    public void marshal(final Exchange exchange, final Object graph, final OutputStream stream) throws Exception {
        String filename;
        String filepath = exchange.getIn().getHeader(FILE_NAME, String.class);
        if (filepath == null) {
            // generate the file name as the camel file component would do
            filename = filepath = StringHelper.sanitize(exchange.getIn().getMessageId());
        } else {
            filename = Paths.get(filepath).getFileName().toString(); // remove any path elements
        }

        TarArchiveOutputStream tos = new TarArchiveOutputStream(stream);
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

        // the tar entry needs the exact size of the content before the content is written,
        // and the CamelFileLength header is not to be trusted as the body may have changed since
        // (CAMEL-26009), so take the size from the body itself
        long filelength;
        InputStream is;
        CachedOutputStream cos = null;
        File file = asFile(graph);
        if (graph instanceof byte[] bytes) {
            filelength = bytes.length;
            is = new ByteArrayInputStream(bytes);
        } else if (file != null) {
            filelength = file.length();
            is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);
        } else {
            // the size is not known up front, so copy the body to a stream cache first
            cos = new CachedOutputStream(exchange);
            InputStream body = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, graph);
            try {
                filelength = copyAndCount(body, cos);
            } finally {
                IOHelper.close(body);
            }
            is = cos.getInputStream();
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
            IOHelper.close(is, tos, cos);
        }

        String newFilename = filename + ".tar";
        exchange.getMessage().setHeader(FILE_NAME, newFilename);
    }

    @Override
    public Object unmarshal(final Exchange exchange, final InputStream stream) throws Exception {
        if (usingIterator) {
            TarIterator tarIterator = new TarIterator(exchange, stream);
            tarIterator.setAllowEmptyDirectory(allowEmptyDirectory);
            tarIterator.setMaxDecompressedSize(maxDecompressedSize);
            return tarIterator;
        } else {
            BufferedInputStream bis = new BufferedInputStream(stream);
            TarArchiveInputStream tis = (TarArchiveInputStream) new ArchiveStreamFactory()
                    .createArchiveInputStream(ArchiveStreamFactory.TAR, bis);
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

            try {
                TarArchiveEntry entry = getNextFileEntry(tis);
                if (entry != null) {
                    exchange.getMessage().setHeader(FILE_NAME, FileUtil.stripPath(entry.getName()));
                    IOHelper.copy(tis, osb, IOHelper.DEFAULT_BUFFER_SIZE, false, maxDecompressedSize);
                } else {
                    throw new IllegalStateException("Unable to untar the file, it may be corrupted.");
                }

                entry = getNextFileEntry(tis);
                if (entry != null) {
                    throw new IllegalStateException("Tar file has more than 1 entry.");
                }

                return osb.build();

            } finally {
                IOHelper.close(osb, tis, bis);
            }
        }
    }

    private static TarArchiveEntry getNextFileEntry(TarArchiveInputStream tis) throws IOException {
        // skip the folder entries, as an archive of a folder with one file has an entry for the folder too
        TarArchiveEntry entry;
        while ((entry = tis.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return entry;
            }
        }
        return null;
    }

    private static File asFile(Object graph) {
        if (graph instanceof File file) {
            return file;
        } else if (graph instanceof WrappedFile<?> wrappedFile && wrappedFile.getFile() instanceof File file) {
            return file;
        }
        return null;
    }

    private static long copyAndCount(InputStream is, OutputStream os) throws IOException {
        long total = 0;
        byte[] buffer = new byte[IOHelper.DEFAULT_BUFFER_SIZE];
        int n;
        while ((n = is.read(buffer)) != -1) {
            os.write(buffer, 0, n);
            total += n;
        }
        os.flush();
        return total;
    }

    private void createTarEntries(TarArchiveOutputStream tos, String filepath, long filelength) throws IOException {
        Iterator<Path> elements = Paths.get(filepath).iterator();
        StringBuilder sb = new StringBuilder(256);

        while (elements.hasNext()) {
            Path path = elements.next();
            String element = path.toString();
            long length = filelength;

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

    public long getMaxDecompressedSize() {
        return maxDecompressedSize;
    }

    public void setMaxDecompressedSize(long maxDecompressedSize) {
        this.maxDecompressedSize = maxDecompressedSize;
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
