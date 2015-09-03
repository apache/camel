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
package org.apache.camel.processor.aggregate.tarfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.processor.aggregate.AggregationStrategy;
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

    private String filePrefix;
    private String fileSuffix = ".tar";
    private boolean preserveFolderStructure;
    private boolean useFilenameHeader;

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

    /**
     * Gets the prefix used when creating the TAR file name.
     * @return the prefix
     */
    public String getFilePrefix() {
        return filePrefix;
    }

    /**
     * Sets the prefix that will be used when creating the TAR filename.
     * @param filePrefix prefix to use on TAR file.
     */
    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    /**
     * Gets the suffix used when creating the TAR file name.
     * @return the suffix
     */
    public String getFileSuffix() {
        return fileSuffix;
    }

    /**
     * Sets the suffix that will be used when creating the ZIP filename.
     * @param fileSuffix suffix to use on ZIP file.
     */
    public void setFileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
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
                tarFile = FileUtil.createTempFile(this.filePrefix, this.fileSuffix, new File(System.getProperty("java.io.tmpdir")));
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
            answer = newExchange;
            answer.addOnCompletion(new DeleteTarFileOnCompletion(tarFile));
        } else {
            tarFile = oldExchange.getIn().getBody(File.class);
        }

        // Handle GenericFileMessages
        if (GenericFileMessage.class.isAssignableFrom(newExchange.getIn().getClass())) {
            try {
                File appendFile = newExchange.getIn().getMandatoryBody(File.class);
                if (appendFile != null) {
                    addFileToTar(tarFile, appendFile, this.preserveFolderStructure ? newExchange.getIn().toString() : null);
                    GenericFile<File> genericFile =
                            FileConsumer.asGenericFile(
                                    tarFile.getParent(),
                                    tarFile,
                                    null);    // Do not set charset here, that will cause the tar file to be handled as ASCII later which breaks it..
                    genericFile.bindToExchange(answer);
                }
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        } else {
            // Handle all other messages
            try {
                byte[] buffer = newExchange.getIn().getMandatoryBody(byte[].class);
                String entryName = useFilenameHeader ? newExchange.getIn().getHeader(Exchange.FILE_NAME, String.class) : newExchange.getIn()
                        .getMessageId();
                addEntryToTar(tarFile, entryName, buffer, buffer.length);
                GenericFile<File> genericFile = FileConsumer.asGenericFile(
                        tarFile.getParent(), tarFile, null);
                genericFile.bindToExchange(answer);
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }

        return answer;
    }

    private static void addFileToTar(File source, File file, String fileName) throws IOException, ArchiveException {
        File tmpTar = File.createTempFile(source.getName(), null);
        tmpTar.delete();
        if (!source.renameTo(tmpTar)) {
            throw new IOException("Could not make temp file (" + source.getName() + ")");
        }

        TarArchiveInputStream tin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, new FileInputStream(tmpTar));
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

        IOHelper.close(in);
        IOHelper.close(tin);
        IOHelper.close(tos);
    }

    private static void addEntryToTar(File source, String entryName, byte[] buffer, int length) throws IOException, ArchiveException {
        File tmpTar = File.createTempFile(source.getName(), null);
        tmpTar.delete();
        if (!source.renameTo(tmpTar)) {
            throw new IOException("Cannot create temp file: " + source.getName());
        }
        TarArchiveInputStream tin = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.TAR, new FileInputStream(tmpTar));
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

        IOHelper.close(tin);
        IOHelper.close(tos);
    }

    /**
     * This callback class is used to clean up the temporary TAR file once the exchange has completed.
     */
    private class DeleteTarFileOnCompletion implements Synchronization {

        private final File fileToDelete;

        public DeleteTarFileOnCompletion(File fileToDelete) {
            this.fileToDelete = fileToDelete;
        }

        @Override
        public void onFailure(Exchange exchange) {
            // Keep the file if something gone a miss.
        }

        @Override
        public void onComplete(Exchange exchange) {
            FileUtil.deleteFile(this.fileToDelete);
        }
    }
}
