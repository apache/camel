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

import org.apache.camel.impl.DefaultMessage;

/**
 * Remote file message
 */
public class RemoteFileMessage extends DefaultMessage {
    private RemoteFile remoteFile;

    public RemoteFileMessage() {
    }

    public RemoteFileMessage(RemoteFile remoteFile) {
        this.remoteFile = remoteFile;
    }

    @Override
    public RemoteFileExchange getExchange() {
        return (RemoteFileExchange) super.getExchange();
    }

    @Override
    protected Object createBody() {
        return remoteFile.getBody();
    }

    public RemoteFile getRemoteFile() {
        return remoteFile;
    }

    public void setRemoteFile(RemoteFile remoteFile) {
        this.remoteFile = remoteFile;
    }

    @Override
    public RemoteFileMessage newInstance() {
        return new RemoteFileMessage();
    }

    @Override
    public String toString() {
        return "RemoteFileMessage: " + remoteFile;
    }
}
