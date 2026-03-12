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
package org.apache.camel.component.file.remote;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Secure FTP consumer using JSch.
 */
public class SftpConsumer extends AbstractSftpConsumer {

    private transient String sftpConsumerToString;

    public SftpConsumer(RemoteFileEndpoint<SftpRemoteFile> endpoint, Processor processor,
                        RemoteFileOperations<SftpRemoteFile> operations,
                        GenericFileProcessStrategy<SftpRemoteFile> processStrategy) {
        super(endpoint, processor, operations, processStrategy);
    }

    @Override
    protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
        if (getEndpoint().getConfiguration().isIgnoreFileNotFoundOrPermissionError()) {
            SftpException sftp = ObjectHelper.getException(SftpException.class, cause);
            if (sftp != null) {
                return sftp.id == ChannelSftp.SSH_FX_NO_SUCH_FILE || sftp.id == ChannelSftp.SSH_FX_PERMISSION_DENIED;
            }
        }
        return super.ignoreCannotRetrieveFile(name, exchange, cause);
    }

    @Override
    protected void updateFileHeaders(GenericFile<SftpRemoteFile> file, Message message) {
        Object rf = file.getFile().getRemoteFile();
        if (rf != null) {
            ChannelSftp.LsEntry e = (ChannelSftp.LsEntry) rf;
            long length = e.getAttrs().getSize();
            long modified = e.getAttrs().getMTime() * 1000L;
            file.setFileLength(length);
            file.setLastModified(modified);
            if (length >= 0) {
                message.setHeader(FtpConstants.FILE_LENGTH, length);
            }
            if (modified >= 0) {
                message.setHeader(FtpConstants.FILE_LAST_MODIFIED, modified);
            }
        }
    }

    @Override
    public String toString() {
        if (sftpConsumerToString == null) {
            sftpConsumerToString = "SftpConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sftpConsumerToString;
    }

}
