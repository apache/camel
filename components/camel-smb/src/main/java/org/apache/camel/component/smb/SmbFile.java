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

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;

public class SmbFile extends GenericFile<FileIdBothDirectoryInformation> {

    private final SmbOperations operations;
    private final boolean streamDownload;
    private Exchange exchange;
    private String hostname;

    public SmbFile(SmbOperations operations, boolean streamDownload) {
        this.operations = operations;
        this.streamDownload = streamDownload;
    }

    @Override
    public void bindToExchange(Exchange exchange) {
        this.exchange = exchange;
        super.bindToExchange(exchange);
    }

    /**
     * Populates the {@link GenericFileMessage} relevant headers
     *
     * @param message the message to populate with headers
     */
    public void populateHeaders(GenericFileMessage<FileIdBothDirectoryInformation> message) {
        if (message != null) {
            // because there is not probeContentType option
            // in other file based components, false may be passed
            // as the second argument.
            super.populateHeaders(message, false);
            message.setHeader(SmbConstants.FILE_HOST, getHostname());
        }
    }

    @Override
    public void populateHeaders(
            GenericFileMessage<FileIdBothDirectoryInformation> message, boolean isProbeContentTypeFromEndpoint) {
        populateHeaders(message);
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public char getFileSeparator() {
        // always use '/' as separator for SMB
        return '/';
    }

    @Override
    public void copyFromPopulateAdditional(
            GenericFile<FileIdBothDirectoryInformation> source, GenericFile<FileIdBothDirectoryInformation> result) {
        SmbFile remoteSource = (SmbFile) source;
        SmbFile remoteResult = (SmbFile) result;
        remoteResult.setHostname(remoteSource.getHostname());
    }

    @Override
    public Object getBody() {
        if (streamDownload) {
            return operations.getBodyAsInputStream(exchange, this.getAbsoluteFilePath());
        } else {
            // use operations so smb file can be closed
            return operations.getBody(this.getAbsoluteFilePath());
        }
    }

    @Override
    public String toString() {
        return "SmbFile[" + (isAbsolute() ? getAbsoluteFilePath() : getRelativeFilePath()) + "]";
    }

}
