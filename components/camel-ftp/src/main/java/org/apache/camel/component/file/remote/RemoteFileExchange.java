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
package org.apache.camel.component.file.remote;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

public class RemoteFileExchange extends DefaultExchange {
    private RemoteFile remoteFile;

    public RemoteFileExchange(CamelContext context, ExchangePattern pattern, RemoteFile remoteFile) {
        super(context, pattern);
        setRemoteFile(remoteFile);
    }

    public RemoteFileExchange(DefaultExchange parent, RemoteFile remoteFile) {
        super(parent);
        setRemoteFile(remoteFile);
    }

    public RemoteFile getRemoteFile() {
        return remoteFile;
    }

    public void setRemoteFile(RemoteFile remoteFile) {
        setIn(new RemoteFileMessage(remoteFile));
        this.remoteFile = remoteFile;
        populateHeaders(remoteFile);
    }

    public Exchange newInstance() {
        return new RemoteFileExchange(this, remoteFile);
    }

    protected void populateHeaders(RemoteFile remoteFile) {
        if (remoteFile != null) {
            getIn().setHeader("file.remote.host", remoteFile.getHostname());
            getIn().setHeader("file.remote.absoluteName", remoteFile.getAbsolutelFileName());
            getIn().setHeader("file.remote.relativeName", remoteFile.getRelativeFileName());
            getIn().setHeader("file.remote.name", remoteFile.getFileName());

            getIn().setHeader("CamelFileName", remoteFile.getFileName());
            getIn().setHeader("CamelFilePath", remoteFile.getAbsolutelFileName());
            // set the parent if there is a parent folder
            if (remoteFile.getAbsolutelFileName() != null && remoteFile.getAbsolutelFileName().indexOf("/") != -1) {
                String parent = remoteFile.getAbsolutelFileName().substring(0, remoteFile.getAbsolutelFileName().lastIndexOf("/"));
                getIn().setHeader("CamelFileParent", parent);
            }
            if (remoteFile.getFileLength() > 0) {
                getIn().setHeader("CamelFileLength", new Long(remoteFile.getFileLength()));
            }
        }
    }

}
