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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isEmpty;

/**
 * SMB file producer
 */
public class SmbProducer extends DefaultProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmbProducer.class);

    private boolean loggedIn;
    private Session session;
    private final SMBClient smbClient;

    protected SmbProducer(final SmbEndpoint endpoint) {
        super(endpoint);

        if (getEndpoint().getConfiguration().getSmbConfig() != null) {
            smbClient = new SMBClient(getEndpoint().getConfiguration().getSmbConfig());
        } else {
            smbClient = new SMBClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOGGER.debug("Producer SMB client stopped");
        super.doStop();
    }

    @Override
    protected void doStart() throws Exception {
        LOGGER.debug("Producer SMB client started");

        super.doStart();
    }

    /*
     * Normalize changes separators for smb
     */
    public String normalize(String file) {
        String result = file.replace('\\', '/');
        LOGGER.debug("Normalize path {} to {}", file, result);
        return result;
    }

    protected void connectIfNecessary(Exchange exchange) throws IOException {
        Connection connection = smbClient.connect(getEndpoint().getHostname(), getEndpoint().getPort());

        if (!loggedIn) {
            LOGGER.debug("Not already connected/logged in. Connecting to: {}", getEndpoint());

            AuthenticationContext ac = new AuthenticationContext(
                    getEndpoint().getConfiguration().getUsername(),
                    getEndpoint().getConfiguration().getPassword().toCharArray(),
                    getEndpoint().getConfiguration().getDomain());
            session = connection.authenticate(ac);

            LOGGER.debug("Connected and logged in to: {}", getEndpoint());
        }
    }

    public void disconnect() throws IOException {
        loggedIn = false;
        LOGGER.debug("Disconnecting from: {}", getEndpoint());

        session.close();

        session = null;
    }

    public void createDirectory(DiskShare share, java.io.File file) {
        String parentDir = file.getParent();
        SmbConfiguration configuration = getEndpoint().getConfiguration();

        boolean dirExists = share.folderExists(parentDir);
        if (!dirExists && configuration.isAutoCreate()) {
            SmbFiles files = new SmbFiles();
            files.mkdirs(share, normalize(parentDir));
        }

        if (!dirExists) {
            throw new RuntimeCamelException("Directory " + parentDir + " does not exist on share " + share.toString());
        }
    }

    private GenericFileExist determineFileExist(Exchange exchange) {
        GenericFileExist gfe = exchange.getIn().getHeader(SmbConstants.SMB_FILE_EXISTS, GenericFileExist.class);
        if (isEmpty(gfe)) {
            gfe = getEndpoint().getConfiguration().getFileExist();
        }

        gfe = (gfe == null) ? GenericFileExist.Fail : gfe;

        return gfe;
    }

    private void doFail(String path) {
        throw new RuntimeCamelException("File " + path + " already exists, cannot create");
    }

    private void doIgnore(String path) {
        // ignore but indicate that the file was written
        LOGGER.debug("An existing file already exists: {}. Ignore and do not override it.", path);
    }

    private void removeFile(DiskShare share, java.io.File file) {
        LOGGER.debug("An existing file already exists: {}. Removing it.", file.getName());
        share.rm(file.getPath());
    }

    @Override
    public void process(final Exchange exchange) {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);

        SmbConfiguration configuration = getEndpoint().getConfiguration();
        String path = (configuration.getPath() == null) ? "" : configuration.getPath();

        LOGGER.debug("storeFile() fileName [" + fileName + "]");

        try {
            connectIfNecessary(exchange);

            java.io.File file = new java.io.File(path + java.io.File.separator + fileName);

            DiskShare share = (DiskShare) session.connectShare(getEndpoint().getShareName());
            createDirectory(share, file);

            GenericFileExist gfe = determineFileExist(exchange);

            SMB2CreateDisposition scd = SMB2CreateDisposition.FILE_OPEN;
            HashSet<AccessMask> accessMask = new HashSet<AccessMask>(Arrays.asList(AccessMask.GENERIC_ALL));
            HashSet<SMB2CreateOptions> createOptions = new HashSet<>(Arrays.asList(SMB2CreateOptions.FILE_DIRECTORY_FILE));
            HashSet<FileAttributes> fileAttributes
                    = new HashSet<FileAttributes>(Arrays.asList(FileAttributes.FILE_ATTRIBUTE_NORMAL));

            // File existence modes
            switch (gfe) {
                case Override:
                    scd = SMB2CreateDisposition.FILE_OVERWRITE_IF;
                    break;
                case Fail:
                    scd = SMB2CreateDisposition.FILE_CREATE;
                    if (share.fileExists(file.getPath())) {
                        doFail(file.getPath());
                    }
                    break;
                case Append:
                    scd = SMB2CreateDisposition.FILE_OPEN_IF;
                    accessMask = new HashSet<AccessMask>(Arrays.asList(AccessMask.FILE_WRITE_DATA));
                    break;
                case Ignore:
                    if (share.fileExists(file.getPath())) {
                        doIgnore(file.getPath());
                        return;
                    }
                    break;
                case Move:
                    throw new UnsupportedOperationException("Move is not implemented for this producer at the moment");
                case TryRename:
                    throw new UnsupportedOperationException("TryRename is not implemented for this producer at the moment");
            }

            File shareFile = share.openFile(file.getPath(),
                    accessMask,
                    fileAttributes,
                    SMB2ShareAccess.ALL,
                    scd,
                    createOptions);

            InputStream is = (exchange.getMessage(InputStream.class) == null)
                    ? exchange.getMessage().getBody(InputStream.class) : exchange.getMessage(InputStream.class);

            // In order to provide append option, we need to use offset / write with shareFile rather
            // than with outputstream
            int buffer = 2048;
            long fileOffset = 0;

            byte[] byteBuffer = new byte[buffer];
            int length = 0;
            while ((length = is.read(byteBuffer)) > 0) {
                fileOffset = share.getFileInformation(file.getPath())
                        .getStandardInformation().getEndOfFile();
                shareFile.write(byteBuffer, fileOffset, 0, length);
            }
            shareFile.flush();
            shareFile.close();

        } catch (IOException ioe) {
            throw new RuntimeCamelException(ioe);
        }
    }

    @Override
    public SmbEndpoint getEndpoint() {
        return (SmbEndpoint) super.getEndpoint();
    }
}
