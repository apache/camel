/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import org.apache.camel.impl.DefaultMessage;

import java.io.OutputStream;
import java.util.Map;

public class RemoteFileMessage extends DefaultMessage {
    private OutputStream outputStream;
    private String fullFileName;
    private String hostname;

    public RemoteFileMessage() {
    }

    public RemoteFileMessage(String hostname, String fullFileName, OutputStream outputStream) {
        this.hostname = hostname;
        this.fullFileName = fullFileName;
        this.outputStream = outputStream;
        setMessageId(hostname + ":" + fullFileName);
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getFullFileName() {
        return fullFileName;
    }

    public void setFullFileName(String fullFileName) {
        this.fullFileName = fullFileName;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public RemoteFileExchange getExchange() {
        return (RemoteFileExchange) super.getExchange();
    }

    @Override
    protected Object createBody() {
        if (outputStream != null) {
            return getExchange().getBinding().extractBodyFromOutputStream(getExchange(), outputStream);
        }
        return null;
    }

    @Override
    public RemoteFileMessage newInstance() {
        return new RemoteFileMessage();
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        super.populateInitialHeaders(map);
        map.put("file.remote.host", hostname);
        map.put("file.remote.name", fullFileName);
    }
}
