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
package org.apache.camel.processor.aggregate.tarfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This aggregation strategy will aggregate all incoming messages into a TAR file.
 * <p>If the incoming exchanges contain {@link GenericFileMessage} file name will 
 * be taken from the body otherwise the body content will be treated as a byte 
 * array and the TAR entry will be named using the message id (unless the flag
 * useFilenameHeader is set to true.</p>
 * <p><b>NOTE 1:</b> Please note that this aggregation strategy requires eager
 * completion check to work properly.</p>
 *
 * <p><b>NOTE 2:</b> This implementation is very inefficient especially on big files since the tar
 * file is completely rewritten for each file that is added to it. Investigate if the
 * files can be collected and at completion stored to tar file.</p>
 */
public class TarAggregationStrategy implements AggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(TarAggregationStrategy.class);

    private String filePrefix;
    private String fileSuffix = ".tar";
    private boolean preserveFolderStructure;
    private boolean useFilenameHeader;
    private File parentDir = new File(System.getProperty("java.io.tmpdir"));

    public TarAggregationStrategy() {
        this(false, false);
    }

    /**
     * @param preserveFolderStructure if true, the folder structure is preserved when the source is
     * a type of {@link GenericFileMessage}.  If used with a file, use recursive=true.
     */
    public TarAggregationStrategy(boolean preserveFolderStructure) {
        this(preserveFolderStructure, false);
    }

    /**
     * @param preserveFolderStructure if true, the folder structure is preserved when the source is
     * a type of {@link GenericFileMessage}.  If used with a file, use recursive=true.
     * @param useFilenameHeader if true, the filename header will be used to name aggregated byte arrays
     * within the TAR file.
     */
    public TarAggregationStrategy(boolean preserveFolderStructure, boolean useFilenameHeader) {
        this.preserveFolderStructure = preserveFolderStructure;
        this.useFilenameHeader = useFilenameHeader;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    /**
     * Sets the prefix that will be used when creating the TAR filename.
     */
    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    /**
     * Sets the suffix that will be used when creating the ZIP filename.
     */
    public void setFileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    public File getParentDir() {
        return parentDir;
    }

    /**
     * Sets the parent directory to use for writing temporary files.
     */
    public void setParentDir(File parentDir) {
        this.parentDir = parentDir;
    }

    /**
     * Sets the parent directory to use for writing temporary files.
     */
    public void setParentDir(String parentDir) {
        this.parentDir = new File(parentDir);
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        File tarFile;
        Exchange answer = oldExchange;

        // Guard against empty new exchanges
        if (newExchange == null) {
            return oldExchange;
        }

        // First time for this aggregation
        if (oldExchange == null) {
            try {
                tarFile = FileUtil.createTempFile(this.filePrefix, this.fileSuffix, this.parentDir);
                LOG.trace("Created temporary file: {}", tarFile);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
            answer = newExchange;
            answer.adapt(ExtendedExchange.class).addOnCompletion(new DeleteTarFileOnCompletion(tarFile));
        } else {
            tarFile = oldExchange.getIn().getBody(File.class);
        }

        Object body = newExchange.getIn().getBody();
        if (body instanceof WrappedFile) {
            body = ((WrappedFile) body).getFile();
        }

        if (body instanceof File) {
            try {
                File appendFile = (File) body;
                // do not try to append empty files
                if (appendFile.length() > 0) {
                    String entryName = preserveFolderStructure ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn().getMessageId();
                    addFileToTar(tarFile, appendFile, this.preserveFolderStructure ? entryName : null);
                    GenericFile<File> genericFile =
                            FileConsumer.asGenericFile(
                                    tarFile.getParent(), tarFile, Charset.defaultCharset().toString(), false);
                    genericFile.bindToExchange(answer);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        } else {
            // Handle all other messages
            try {
                byte[] buffer = newExchange.getIn().getMandatoryBody(byte[].class);
                // do not try to append empty data
                if (buffer.length > 0) {
                    String entryName = useFilenameHeader ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn().getMessageId();
                    addEntryToTar(tarFile, entryName, buffer, buffer.length);
                    GenericFile<File> genericFile = FileConsumer.asGenericFile(
                            tarFile.getParent(), tarFile, Charset.defaultCharset().toString(), false);
                    genericFile.bindToExchange(answer);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
        return answer;
    }

    private void addFileToTar(File source, File file, String fileName) throws IOException, ArchiveException {
        File tmpTar = Files.createTempFile(parentDir.toPath(), source.getName(), null).toFile();
        tmpTar.delete();
        if (!source.renameTo(tmpTar)) {
            throw new IOException("Could not make temp file (" + source.getName() + ")");
        }

        FileInputStream fis = new FileInputStream(tmpTar);
        TarArchiveInputStream tin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, fis);
        TarArchiveOutputStream tos = new TarArchiveOutputStream(new FileOutputStream(source));
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

        InputStream in = new FileInputStream(file);

        // copy the existing entries    
        ArchiveEntry nextEntry;
        while ((nextEntry = tin.getNextEntry()) != null) {
            tos.putArchiveEntry(nextEntry);
            IOUtils.copy(tin, tos);
            tos.closeArchiveEntry();
        }

        // Add the new entry
        TarArchiveEntry entry = new TarArchiveEntry(fileName == null ? file.getName() : fileName);
        entry.setSize(file.length());
        tos.putArchiveEntry(entry);
        IOUtils.copy(in, tos);
        tos.closeArchiveEntry();

        IOHelper.close(fis, in, tin, tos);
        LOG.trace("Deleting temporary file: {}", tmpTar);
        FileUtil.deleteFile(tmpTar);
    }

    private void addEntryToTar(File source, String entryName, byte[] buffer, int length) throws IOException, ArchiveException {
        File tmpTar = Files.createTempFile(parentDir.toPath(), source.getName(), null).toFile();
        tmpTar.delete();
        if (!source.renameTo(tmpTar)) {
            throw new IOException("Cannot create temp file: " + source.getName());
        }

        FileInputStream fis = new FileInputStream(tmpTar);
        TarArchiveInputStream tin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, fis);
        TarArchiveOutputStream tos = new TarArchiveOutputStream(new FileOutputStream(source));
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

        // copy the existing entries    
        ArchiveEntry nextEntry;
        while ((nextEntry = tin.getNextEntry()) != null) {
            tos.putArchiveEntry(nextEntry);
            IOUtils.copy(tin, tos);
            tos.closeArchiveEntry();
        }

        // Create new entry
        TarArchiveEntry entry = new TarArchiveEntry(entryName);
        entry.setSize(length);
        tos.putArchiveEntry(entry);
        tos.write(buffer, 0, length);
        tos.closeArchiveEntry();

        IOHelper.close(fis, tin, tos);
        LOG.trace("Deleting temporary file: {}", tmpTar);
        FileUtil.deleteFile(tmpTar);
    }

    /**
     * This callback class is used to clean up the temporary TAR file once the exchange has completed.
     */
    private class DeleteTarFileOnCompletion implements Synchronization {

        private final File fileToDelete;

        DeleteTarFileOnCompletion(File fileToDelete) {
            this.fileToDelete = fileToDelete;
        }

        @Override
        public void onFailure(Exchange exchange) {
            // Keep the file if something gone a miss.
        }

        @Override
        public void onComplete(Exchange exchange) {
            LOG.debug("Deleting tar file on completion: {}", this.fileToDelete);
            FileUtil.deleteFile(this.fileToDelete);
        }
    }
}
