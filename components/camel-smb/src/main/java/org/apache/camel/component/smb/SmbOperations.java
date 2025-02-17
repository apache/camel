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
package org.apache.camel.component.smb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.utils.SmbFiles;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class SmbOperations implements SmbFileOperations {

    private static final Logger LOG = LoggerFactory.getLogger(SmbOperations.class);

    private final SmbConfiguration configuration;
    private SmbEndpoint endpoint;
    private boolean loggedIn;
    private Session session;
    private DiskShare share;
    private SMBClient smbClient;

    public SmbOperations(SmbConfiguration configuration) {
        this.configuration = configuration;
        this.smbClient = configuration == null || configuration.getSmbConfig() == null ? new SMBClient() : new SMBClient(configuration.getSmbConfig());
    }

    @Override
    public boolean connect(SmbConfiguration configuration, Exchange exchange) throws GenericFileOperationFailedException {
        connectIfNecessary();
        return true;
    }

    protected void connectIfNecessary() {
        try {
            Connection connection = smbClient.connect(configuration.getHostname(), configuration.getPort());

            if (!loggedIn || !isConnected()) {
                LOG.debug("Not already connected/logged in. Connecting to: {}:{}", configuration.getHostname(),
                        configuration.getPort());

                AuthenticationContext ac = new AuthenticationContext(
                        configuration.getUsername(),
                        configuration.getPassword().toCharArray(),
                        configuration.getDomain());
                session = connection.authenticate(ac);

                share = (DiskShare) session.connectShare(configuration.getShareName());

                LOG.debug("Connected and logged in to: {}:{}", configuration.getHostname(), configuration.getPort());
                loggedIn = true;
            }
        } catch (IOException e) {
            disconnect();
            throw new GenericFileOperationFailedException(
                    "Cannot connect to: " + configuration.getHostname() + ":" + configuration.getPort() + " due to: "
                                                          + e.getMessage(),
                    e);
        }
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        if (share != null) {
            return share.isConnected();
        }
        return false;
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        loggedIn = false;
        if (share != null) {
            try {
                share.close();
            } catch (IOException e) {
                // ignore
            }
            share = null;
        }
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                // ignore
            }
            session = null;
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        disconnect();
        if (smbClient != null) {
            smbClient.close();
        }
        smbClient = null;
    }

    @Override
    public GenericFile<FileIdBothDirectoryInformation> newGenericFile() {
        return new SmbFile(this, configuration.isDownload(), configuration.isStreamDownload());
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<FileIdBothDirectoryInformation> endpoint) {
        this.endpoint = (SmbEndpoint) endpoint;
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        connectIfNecessary();
        if (share.fileExists(name)) {
            try (File f = share.openFile(name, EnumSet.of(AccessMask.GENERIC_ALL), null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN, null)) {

                f.deleteOnClose();
            }
        }
        return true;
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        return share.fileExists(name);
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        connectIfNecessary();
        try (File src
                = share.openFile(from, EnumSet.of(AccessMask.GENERIC_ALL), null,
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {

            try (File dst
                    = share.openFile(to, EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
                            EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE))) {

                src.remoteCopyTo(dst);
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }

            src.deleteOnClose();
        }
        return true;
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        connectIfNecessary();
        SmbFiles files = new SmbFiles();
        files.mkdirs(share, normalize(directory));
        return true;
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        if (isNotEmpty(endpoint.getLocalWorkDirectory())) {
            // local work directory is configured so we should store file
            // content as files in this local directory
            return retrieveFileToFileInLocalWorkDirectory(name, exchange);
        } else {
            // store file content directory as stream on the body
            return retrieveFileToStreamInBody(name, exchange);
        }
    }

    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        SmbFile target = (SmbFile) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

        connectIfNecessary();

        // read the entire file into memory in the byte array
        try (File shareFile = share.openFile(name, EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {

            if (configuration.isStreamDownload()) {
                InputStream is = shareFile.getInputStream();
                target.setBody(is);
                exchange.getIn().setHeader(SmbConstants.SMB_FILE_INPUT_STREAM, is);
            } else {
                try (InputStream is = shareFile.getInputStream()) {
                    byte[] body = is.readAllBytes();
                    target.setBody(body);
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(e.getMessage(), e);
                }
            }
        }
        return true;
    }

    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange)
            throws GenericFileOperationFailedException {
        java.io.File temp;
        java.io.File local = new java.io.File(endpoint.getLocalWorkDirectory());
        SmbFile file = (SmbFile) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(file, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
        try {
            // use relative filename in local work directory
            String relativeName = file.getRelativeFilePath();

            temp = new java.io.File(local, relativeName + ".inprogress");

            // create directory to local work file
            local.mkdirs();
            local = new java.io.File(local, relativeName);

            // delete any existing files
            if (temp.exists()) {
                if (!FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
            }
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // create new temp local work file
            if (!temp.createNewFile()) {
                throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
            }

            // set header with the path to the local work file
            exchange.getIn().setHeader(SmbConstants.FILE_LOCAL_WORK_PATH, local.getPath());
        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }
        try {
            file.setBody(local);
            try (File shareFile = share.openFile(name, EnumSet.of(AccessMask.GENERIC_READ), null,
                    SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {

                try (InputStream is = shareFile.getInputStream()) {
                    // store content as a file in the local work directory in the temp handle
                    java.nio.file.Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {

            LOG.trace("Error occurred during retrieving file: {} to local directory. Deleting local work file: {}", name, temp);
            // failed to retrieve the file so we need to close streams and delete in progress file
            boolean deleted = FileUtil.deleteFile(temp);
            if (!deleted) {
                LOG.warn("Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                        name, temp);
            }
            disconnect();
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        }

        // operation went okay so rename temp to local after we have retrieved the data
        LOG.trace("Renaming local in progress file from: {} to: {}", temp, local);
        try {
            if (!FileUtil.renameFile(temp, local, false)) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local, e);
        }

        return true;
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getHeader(SmbComponent.SMB_FILE_INPUT_STREAM, InputStream.class);
        if (is != null) {
            try {
                IOHelper.close(is);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        connectIfNecessary();
        return doStoreFile(name, exchange);
    }

    private boolean doStoreFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        LOG.trace("doStoreFile({})", name);

        // for backwards compatibility for existing smb that uses a header to control 'file-exist'
        GenericFileExist gfe = exchange.getIn().getHeader(SmbConstants.SMB_FILE_EXISTS, GenericFileExist.class);
        if (gfe == null) {
            gfe = endpoint.getFileExist();
        }

        boolean existFile = false;
        // if an existing file already exists what should we do?
        if (gfe == GenericFileExist.Ignore || gfe == GenericFileExist.Fail
                || gfe == GenericFileExist.Move || gfe == GenericFileExist.Append
                || gfe == GenericFileExist.Override) {
            existFile = share.fileExists(name);
            if (existFile && gfe == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && gfe == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && gfe == GenericFileExist.Move) {
                // move any existing file first
                this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, name);
            }
        }

        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                LOG.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[] {});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                String charset = endpoint.getCharset();
                if (charset != null) {
                    // charset configured so we must convert to the desired
                    // charset so we can write with encoding
                    is = new ByteArrayInputStream(exchange.getIn().getMandatoryBody(String.class).getBytes(charset));
                    LOG.trace("Using InputStream {} with charset {}.", is, charset);
                } else {
                    is = exchange.getIn().getMandatoryBody(InputStream.class);
                }
            }

            final StopWatch watch = new StopWatch();
            LOG.debug("About to store file: {} using stream: {}", name, is);
            if (existFile && gfe == GenericFileExist.Append) {
                LOG.trace("Client appendFile: {}", name);
                try (File shareFile = share.openFile(name, EnumSet.of(AccessMask.FILE_WRITE_DATA),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN_IF,
                        EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE))) {
                    writeToFile(name, shareFile, is);
                }
            } else if (existFile && gfe == GenericFileExist.Override) {
                LOG.trace("Client overrideFile: {}", name);
                try (File shareFile = share.openFile(name, EnumSet.of(AccessMask.FILE_WRITE_DATA),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF,
                        EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE))) {
                    writeToFile(name, shareFile, is);
                }
            } else {
                LOG.trace("Client createFile: {}", name);
                createDirectory(share, name);
                try (File shareFile = share.openFile(name, EnumSet.of(AccessMask.FILE_WRITE_DATA),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
                        EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE))) {
                    writeToFile(name, shareFile, is);
                }
            }
            if (LOG.isDebugEnabled()) {
                long time = watch.taken();
                LOG.debug("Took {} ({} millis) to store file: {}",
                        TimeUtils.printDuration(time, true), time, name);
            }
            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, LOG);
        }
    }

    public void createDirectory(DiskShare share, String fileName) {
        String parentDir = FileUtil.onlyPath(fileName);
        boolean dirExists = share.folderExists(parentDir);
        if (!dirExists) {
            if (endpoint.isAutoCreate()) {
                SmbFiles files = new SmbFiles();
                files.mkdirs(share, normalize(parentDir));
            } else {
                throw new RuntimeCamelException("Directory " + parentDir + " does not exist on share " + share);
            }
        }
    }

    private void writeToFile(String fileName, File shareFile, InputStream is) throws IOException {
        // In order to provide append option, we need to use offset / write with shareFile rather
        // than with outputstream
        int buffer = endpoint.getBufferSize();
        long fileOffset;

        byte[] byteBuffer = new byte[buffer];
        int length;
        while ((length = is.read(byteBuffer)) > 0) {
            fileOffset = share.getFileInformation(fileName)
                    .getStandardInformation().getEndOfFile();
            shareFile.write(byteBuffer, fileOffset, 0, length);
        }
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        return null; // noop
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        // noop
    }

    @Override
    public FileIdBothDirectoryInformation[] listFiles() throws GenericFileOperationFailedException {
        return listFiles("/");
    }

    @Override
    public FileIdBothDirectoryInformation[] listFiles(String path) throws GenericFileOperationFailedException {
        return listFiles(path, null);
    }

    public FileIdBothDirectoryInformation[] listFiles(String path, String searchPattern)
            throws GenericFileOperationFailedException {
        connectIfNecessary();
        return share.list(path, searchPattern).toArray(FileIdBothDirectoryInformation[]::new);
    }

    public byte[] getBody(String path) {
        connectIfNecessary();
        try (File shareFile = share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {

            try (InputStream is = shareFile.getInputStream()) {
                return is.readAllBytes();
            } catch (Exception e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
    }

    public InputStream getBodyAsInputStream(Exchange exchange, String path) {
        connectIfNecessary();
        File shareFile = share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
        InputStream is = shareFile.getInputStream();
        exchange.getIn().setHeader(SmbComponent.SMB_FILE_INPUT_STREAM, is);
        return is;
    }

    /*
     * Normalize changes separators for smb
     */
    private String normalize(String file) {
        return file.replace('\\', endpoint.getFileSeparator());
    }
}
