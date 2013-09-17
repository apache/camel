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
package org.apache.camel.component.jsch;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.List;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SCP remote file operations
 */
public class ScpOperations implements RemoteFileOperations<ScpFile> {
    private static final String DEFAULT_KNOWN_HOSTS = "META-INF/.ssh/known_hosts";
    private static final Logger LOG = LoggerFactory.getLogger(ScpOperations.class);

    private ScpEndpoint endpoint;
    private Session session;
    private ChannelExec channel;

    @Override
    public void setEndpoint(GenericFileEndpoint<ScpFile> endpoint) {
        this.endpoint = (ScpEndpoint)endpoint;
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'delete' not supported by the scp: protocol");
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        // maybe... cannot determine using the scp: protocol
        return false;
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'rename' not supported by the scp: protocol");
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        // done by the server
        return true;
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        // TODO: implement
        return false;
    }
    
    @Override
    public void releaseRetreivedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        // No-op   
    }

    @Override
    public boolean storeFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        ObjectHelper.notNull(session, "session");
        ScpConfiguration cfg = endpoint.getConfiguration();
        
        int timeout = cfg.getConnectTimeout();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Opening channel to {} with {} timeout...", cfg.remoteServerInformation(),
                timeout > 0 ? (Integer.toString(timeout) + " ms") : "no");
        }
        String file = getRemoteFile(name, cfg);


        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                LOG.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[]{});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(getScpCommand(cfg, file));
            channel.connect(timeout);
            LOG.trace("Channel connected to {}", cfg.remoteServerInformation());

            try {
                if (is == null) {
                    is = exchange.getIn().getMandatoryBody(InputStream.class);
                }
                write(channel, file, is, cfg);
            } catch (InvalidPayloadException e) {
                throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException("Failed to write file " + file, e);
            } finally {
                // must close stream after usage
                IOHelper.close(is);
            }
        } catch (JSchException e) {
            throw new GenericFileOperationFailedException("Failed to write file " + file, e);
        } finally {
            if (channel != null) {
                LOG.trace("Disconnecting 'exec' scp channel");
                channel.disconnect();
                channel = null;
                LOG.trace("Channel disconnected from {}", cfg.remoteServerInformation());
            }
        }
        return true;
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        return endpoint.getConfiguration().getDirectory();
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'cd " + path + "' not supported by the scp: protocol");
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'cd ..' not supported by the scp: protocol");
    }

    @Override
    public List<ScpFile> listFiles() throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'ls' not supported by the scp: protocol");
    }

    @Override
    public List<ScpFile> listFiles(String path) throws GenericFileOperationFailedException {
        throw new GenericFileOperationFailedException("Operation 'ls " + path + "' not supported by the scp: protocol");
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException {
        if (!isConnected()) {
            session = createSession(configuration instanceof ScpConfiguration ? (ScpConfiguration)configuration : null);
            // TODO: deal with reconnection attempts
            if (!isConnected()) {
                session = null;
                throw new GenericFileOperationFailedException("Failed to connect to " + configuration.remoteServerInformation());
            }
        }
        return true;
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        return session != null && session.isConnected();
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        if (isConnected()) {
            session.disconnect();
        }
        session = null;
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        // not supported by scp:
        return true;
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        // TODO: not really used, maybe implement at a later time
        return true;
    }
    
    private Session createSession(ScpConfiguration config) {
        ObjectHelper.notNull(config, "ScpConfiguration");
        try {
            final JSch jsch = new JSch();
            // get from configuration
            if (ObjectHelper.isNotEmpty(config.getCiphers())) {
                LOG.debug("Using ciphers: {}", config.getCiphers());
                Hashtable<String, String> ciphers = new Hashtable<String, String>();
                ciphers.put("cipher.s2c", config.getCiphers());
                ciphers.put("cipher.c2s", config.getCiphers());
                JSch.setConfig(ciphers);
            }
            if (ObjectHelper.isNotEmpty(config.getPrivateKeyFile())) {
                LOG.debug("Using private keyfile: {}", config.getPrivateKeyFile());
                String pkfp = config.getPrivateKeyFilePassphrase();
                jsch.addIdentity(config.getPrivateKeyFile(), ObjectHelper.isNotEmpty(pkfp) ? pkfp : null);
            }

            String knownHostsFile = config.getKnownHostsFile();
            jsch.setKnownHosts(ObjectHelper.isEmpty(knownHostsFile) ? DEFAULT_KNOWN_HOSTS : knownHostsFile);
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setTimeout(config.getTimeout());
            session.setUserInfo(new SessionUserInfo(config));
            
            if (ObjectHelper.isNotEmpty(config.getStrictHostKeyChecking())) {
                LOG.debug("Using StrickHostKeyChecking: {}", config.getStrictHostKeyChecking());
                session.setConfig("StrictHostKeyChecking", config.getStrictHostKeyChecking());
            }

            int timeout = config.getConnectTimeout();
            LOG.debug("Connecting to {} with {} timeout...", config.remoteServerInformation(),
                timeout > 0 ? (Integer.toString(timeout) + " ms") : "no");
            if (timeout > 0) {
                session.connect(timeout);
            } else {
                session.connect();
            }
        } catch (JSchException e) {
            session = null;
            LOG.warn("Could not create ssh session for " + config.remoteServerInformation(), e);
        }
        return session;
    }
    
    private void write(ChannelExec c, String name, InputStream data, ScpConfiguration cfg) throws IOException {
        OutputStream os = c.getOutputStream();
        InputStream is = c.getInputStream();

        try {
            writeFile(name, data, os, is, cfg);
        } finally {
            IOHelper.close(is, os);
        }
    }

    private void writeFile(String filename, InputStream data, OutputStream os, InputStream is, ScpConfiguration cfg) throws IOException {
        final int lineFeed = '\n';
        String bytes;
        int pos = filename.indexOf('/');
        if (pos >= 0) {
            // write to child directory
            String dir = filename.substring(0, pos);
            bytes = "D0775 0 " + dir;
            LOG.trace("[scp:sink] {}", bytes);
            os.write(bytes.getBytes());
            os.write(lineFeed);
            os.flush();
            readAck(is, false);

            writeFile(filename.substring(pos + 1), data, os, is, cfg);

            bytes = "E";
            LOG.trace("[scp:sink] {}", bytes);
            os.write(bytes.getBytes());
            os.write(lineFeed);
            os.flush();
            readAck(is, false);
        } else {
            int count = 0;
            int read;
            int size = endpoint.getBufferSize();
            byte[] reply = new byte[size];

            // figure out the stream size as we need to pass it in the header
            BufferedInputStream buffer = new BufferedInputStream(data, size);
            try {
                buffer.mark(Integer.MAX_VALUE);
                while ((read = buffer.read(reply)) != -1) {
                    count += read;
                }

                // send the header
                bytes = "C0" + cfg.getChmod() + " " + count + " " + filename;
                LOG.trace("[scp:sink] {}", bytes);
                os.write(bytes.getBytes());
                os.write(lineFeed);
                os.flush();
                readAck(is, false);

                // now send the stream
                buffer.reset();
                while ((read = buffer.read(reply)) != -1) {
                    os.write(reply, 0, read);
                }
                writeAck(os);
                readAck(is, false);
            } finally {
                IOHelper.close(buffer);
            }
        }
    }

    private void writeAck(OutputStream os) throws IOException {
        os.write(0);
        os.flush();
    }

    private int readAck(InputStream is, boolean failOnEof) throws IOException {
        String message;
        int answer = is.read();
        switch (answer) {
        case -1:
            if (failOnEof) {
                message = "[scp] Unexpected end of stream";
                throw new EOFException(message);
            }
            break;
        case 1:
            message = "[scp] WARN " + readLine(is);
            LOG.warn(message);
            break;
        case 2:
            message = "[scp] NACK " + readLine(is);
            throw new IOException(message);
        default:
        // case 0:
            break;
        }
        return answer;
    }
    
    private String readLine(InputStream is) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            int c;
            do {
                c = is.read();
                if (c == '\n') {
                    return bytes.toString();
                }
                bytes.write(c);
            } while (c != -1);
        } finally {
            IOHelper.close(bytes);
        }

        String message = "[scp] Unexpected end of stream";
        throw new IOException(message);
    }

    private static String getRemoteTarget(ScpConfiguration config) {
        // use current dir (".") if target directory not specified in uri
        return config.getDirectory().isEmpty() ? "." : config.getDirectory();
    }

    private static String getRemoteFile(String name, ScpConfiguration config) {
        String dir = config.getDirectory();
        dir = dir.endsWith("/") ? dir : dir + "/";
        return name.startsWith(dir) ? name.substring(dir.length()) : name;
    }

    private static boolean isRecursiveScp(String name) {
        return name.indexOf('/') > 0;
    }

    private static String getScpCommand(ScpConfiguration config, String name) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("scp ");
        // TODO: need config for scp *-p* (preserves modification times, access times, and modes from the original file)
        // String command="scp " + (ptimestamp ? "-p " : "") + "-t " + configuration.getDirectory();
        // TODO: refactor to use generic command
        cmd.append(isRecursiveScp(name) ? "-r " : "");
        cmd.append("-t ");
        cmd.append(getRemoteTarget(config));
        return cmd.toString(); 
    }

    protected static final class SessionUserInfo implements UserInfo, UIKeyboardInteractive {
        private final ScpConfiguration config;
        public SessionUserInfo(ScpConfiguration config) {
            ObjectHelper.notNull(config, "config");
            this.config = config;
        }
        
        @Override
        public String getPassphrase() {
            LOG.warn("Private Key authentication not supported");
            return null;
        }
        @Override
        public String getPassword() {
            LOG.debug("Providing password for ssh authentication of user '{}'", config.getUsername());
            return config.getPassword();
        }
        @Override
        public boolean promptPassword(String message) {
            LOG.debug(message);
            return true;
        }
        @Override
        public boolean promptPassphrase(String message) {
            LOG.debug(message);
            return true;
        }
        @Override
        public boolean promptYesNo(String message) {
            LOG.debug(message);
            return false;
        }
        @Override
        public void showMessage(String message) {
            LOG.debug(message);
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, 
            String instruction, String[] prompt, boolean[] echo) {
            LOG.debug(instruction);
            // Called for either SSH_MSG_USERAUTH_INFO_REQUEST or SSH_MSG_USERAUTH_PASSWD_CHANGEREQ
            // The most secure choice (especially for the second case) is to return null
            return null;
        }
    }
}
