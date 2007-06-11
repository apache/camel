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

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import org.apache.camel.Processor;

public class SftpEndpoint extends RemoteFileEndpoint<RemoteFileExchange> {
    public SftpEndpoint(String uri, RemoteFileComponent remoteFileComponent, RemoteFileConfiguration configuration) {
        super(uri, remoteFileComponent, configuration);
    }

    public SftpProducer createProducer() throws Exception {
        return new SftpProducer(this, createChannelSftp());
    }

    public SftpConsumer createConsumer(Processor processor) throws Exception {
        final SftpConsumer consumer = new SftpConsumer(this, processor, createChannelSftp());
        configureConsumer(consumer);
        return consumer;
    }

    protected ChannelSftp createChannelSftp() throws JSchException {
        final JSch jsch = new JSch();
        final Session session = jsch.getSession(getConfiguration().getUsername(), getConfiguration().getHost());
        // TODO there's got to be a better way to deal with accepting new hosts...
        session.setUserInfo(new UserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return SftpEndpoint.this.getConfiguration().getPassword();
            }

            public boolean promptPassword(String string) {
                return true;
            }

            public boolean promptPassphrase(String string) {
                return true;
            }

            public boolean promptYesNo(String string) {
                return true;
            }

            public void showMessage(String string) {
            }
        });
        session.connect();
        final ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        return channel;
    }
}
