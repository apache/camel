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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * SFTP remote file operations
 * <p/>
 * The JSCH session and channel are not thread-safe so we need to synchronize access to using this operation.
 */
public class SftpOperations implements RemoteFileOperations<SftpRemoteFile> {
    private static final Logger LOG = LoggerFactory.getLogger(SftpOperations.class);
    private static final Pattern UP_DIR_PATTERN = Pattern.compile("/[^/]+");
    private Proxy proxy;
    private SftpEndpoint endpoint;
    private ChannelSftp channel;
    private Session session;

    public SftpOperations() {
    }

    public SftpOperations(Proxy proxy) {
        this.proxy = proxy;
    }

    /**
     * Extended user info which supports interactive keyboard mode, by entering the password.
     */
    public interface ExtendedUserInfo extends UserInfo, UIKeyboardInteractive {
    }

    public void setEndpoint(GenericFileEndpoint<SftpRemoteFile> endpoint) {
        this.endpoint = (SftpEndpoint) endpoint;
    }

    public synchronized boolean connect(RemoteFileConfiguration configuration) throws GenericFileOperationFailedException {
        if (isConnected()) {
            // already connected
            return true;
        }

        boolean connected = false;
        int attempt = 0;

        while (!connected) {
            try {
                if (LOG.isTraceEnabled() && attempt > 0) {
                    LOG.trace("Reconnect attempt #{} connecting to + {}", attempt, configuration.remoteServerInformation());
                }

                if (channel == null || !channel.isConnected()) {
                    if (session == null || !session.isConnected()) {
                        LOG.trace("Session isn't connected, trying to recreate and connect.");
                        session = createSession(configuration);
                        if (endpoint.getConfiguration().getConnectTimeout() > 0) {
                            LOG.trace("Connecting use connectTimeout: " + endpoint.getConfiguration().getConnectTimeout() + " ...");
                            session.connect(endpoint.getConfiguration().getConnectTimeout());
                        } else {
                            LOG.trace("Connecting ...");
                            session.connect();
                        }
                    }

                    LOG.trace("Channel isn't connected, trying to recreate and connect.");
                    channel = (ChannelSftp) session.openChannel("sftp");

                    if (endpoint.getConfiguration().getConnectTimeout() > 0) {
                        LOG.trace("Connecting use connectTimeout: " + endpoint.getConfiguration().getConnectTimeout() + " ...");
                        channel.connect(endpoint.getConfiguration().getConnectTimeout());
                    } else {
                        LOG.trace("Connecting ...");
                        channel.connect();
                    }
                    LOG.debug("Connected to " + configuration.remoteServerInformation());
                }

                // yes we could connect
                connected = true;
            } catch (Exception e) {
                // check if we are interrupted so we can break out
                if (Thread.currentThread().isInterrupted()) {
                    throw new GenericFileOperationFailedException("Interrupted during connecting", new InterruptedException("Interrupted during connecting"));
                }

                GenericFileOperationFailedException failed = new GenericFileOperationFailedException("Cannot connect to " + configuration.remoteServerInformation(), e);
                LOG.trace("Cannot connect due: {}", failed.getMessage());
                attempt++;
                if (attempt > endpoint.getMaximumReconnectAttempts()) {
                    throw failed;
                }
                if (endpoint.getReconnectDelay() > 0) {
                    try {
                        Thread.sleep(endpoint.getReconnectDelay());
                    } catch (InterruptedException ie) {
                        // we could potentially also be interrupted during sleep
                        Thread.currentThread().interrupt();
                        throw new GenericFileOperationFailedException("Interrupted during sleeping", ie);
                    }
                }
            }
        }

        configureBulkRequests();

        return true;
    }

    private void configureBulkRequests() {
        try {
            tryConfigureBulkRequests();
        } catch (JSchException e) {
            throw new GenericFileOperationFailedException("Failed to configure number of bulk requests", e);
        }
    }

    private void tryConfigureBulkRequests() throws JSchException {
        Integer bulkRequests = endpoint.getConfiguration().getBulkRequests();

        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);

            channel.setBulkRequests(bulkRequests);
        }
    }

    protected Session createSession(final RemoteFileConfiguration configuration) throws JSchException {
        final JSch jsch = new JSch();
        JSch.setLogger(new JSchLogger(endpoint.getConfiguration().getJschLoggingLevel()));

        SftpConfiguration sftpConfig = (SftpConfiguration) configuration;

        if (isNotEmpty(sftpConfig.getCiphers())) {
            LOG.debug("Using ciphers: {}", sftpConfig.getCiphers());
            Hashtable<String, String> ciphers = new Hashtable<String, String>();
            ciphers.put("cipher.s2c", sftpConfig.getCiphers());
            ciphers.put("cipher.c2s", sftpConfig.getCiphers());
            JSch.setConfig(ciphers);
        }

        if (isNotEmpty(sftpConfig.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: {}", sftpConfig.getPrivateKeyFile());
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                jsch.addIdentity(sftpConfig.getPrivateKeyFile(), sftpConfig.getPrivateKeyPassphrase());
            } else {
                jsch.addIdentity(sftpConfig.getPrivateKeyFile());
            }
        }

        if (sftpConfig.getPrivateKey() != null) {
            LOG.debug("Using private key information from byte array");
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                try {
                    passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new JSchException("Cannot transform passphrase to byte[]", e);
                }
            }
            jsch.addIdentity("ID", sftpConfig.getPrivateKey(), null, passphrase);
        }

        if (sftpConfig.getPrivateKeyUri() != null) {
            LOG.debug("Using private key uri : {}", sftpConfig.getPrivateKeyUri());
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                try {
                    passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new JSchException("Cannot transform passphrase to byte[]", e);
                }
            }
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(), sftpConfig.getPrivateKeyUri());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                jsch.addIdentity("ID", bos.toByteArray(), null, passphrase);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + sftpConfig.getPrivateKeyUri(), e);
            }
        }

        if (sftpConfig.getKeyPair() != null) {
            LOG.debug("Using private key information from key pair");
            KeyPair keyPair = sftpConfig.getKeyPair();
            if (keyPair.getPrivate() != null && keyPair.getPublic() != null) {
                if (keyPair.getPrivate() instanceof RSAPrivateKey && keyPair.getPublic() instanceof RSAPublicKey) {
                    jsch.addIdentity(new RSAKeyPairIdentity("ID", keyPair), null);
                } else if (keyPair.getPrivate() instanceof DSAPrivateKey && keyPair.getPublic() instanceof DSAPublicKey) {
                    jsch.addIdentity(new DSAKeyPairIdentity("ID", keyPair), null);
                } else {
                    LOG.warn("Only RSA and DSA key pairs are supported");
                }
            } else {
                LOG.warn("PrivateKey and PublicKey in the KeyPair must be filled");
            }
        }

        if (isNotEmpty(sftpConfig.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: {}", sftpConfig.getKnownHostsFile());
            jsch.setKnownHosts(sftpConfig.getKnownHostsFile());
        }

        if (isNotEmpty(sftpConfig.getKnownHostsUri())) {
            LOG.debug("Using known hosts uri: {}", sftpConfig.getKnownHostsUri());
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(), sftpConfig.getKnownHostsUri());
                jsch.setKnownHosts(is);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + sftpConfig.getKnownHostsUri(), e);
            }
        }

        if (sftpConfig.getKnownHosts() != null) {
            LOG.debug("Using known hosts information from byte array");
            jsch.setKnownHosts(new ByteArrayInputStream(sftpConfig.getKnownHosts()));
        }

        String knownHostsFile = sftpConfig.getKnownHostsFile();
        if (knownHostsFile == null && sftpConfig.isUseUserKnownHostsFile()) {
            knownHostsFile = System.getProperty("user.home") + "/.ssh/known_hosts";
            LOG.info("Known host file not configured, using user known host file: {}", knownHostsFile);
        }
        if (ObjectHelper.isNotEmpty(knownHostsFile)) {
            LOG.debug("Using known hosts information from file: {}", knownHostsFile);
            jsch.setKnownHosts(knownHostsFile);
        }

        final Session session = jsch.getSession(configuration.getUsername(), configuration.getHost(), configuration.getPort());

        if (isNotEmpty(sftpConfig.getStrictHostKeyChecking())) {
            LOG.debug("Using StrickHostKeyChecking: {}", sftpConfig.getStrictHostKeyChecking());
            session.setConfig("StrictHostKeyChecking", sftpConfig.getStrictHostKeyChecking());
        }

        session.setServerAliveInterval(sftpConfig.getServerAliveInterval());
        session.setServerAliveCountMax(sftpConfig.getServerAliveCountMax());

        // compression
        if (sftpConfig.getCompression() > 0) {
            LOG.debug("Using compression: {}", sftpConfig.getCompression());
            session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
            session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
            session.setConfig("compression_level", Integer.toString(sftpConfig.getCompression()));
        }

        // set the PreferredAuthentications
        if (sftpConfig.getPreferredAuthentications() != null) {
            LOG.debug("Using PreferredAuthentications: {}", sftpConfig.getPreferredAuthentications());
            session.setConfig("PreferredAuthentications", sftpConfig.getPreferredAuthentications());
        }

        // set user information
        session.setUserInfo(new ExtendedUserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return configuration.getPassword();
            }

            public boolean promptPassword(String s) {
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                LOG.warn("Server asks for confirmation (yes|no): " + s + ". Camel will answer no.");
                // Return 'false' indicating modification of the hosts file is disabled.
                return false;
            }

            public void showMessage(String s) {
                LOG.trace("Message received from Server: " + s);
            }

            public String[] promptKeyboardInteractive(String destination, String name,
                                                      String instruction, String[] prompt, boolean[] echo) {
                // must return an empty array if password is null
                if (configuration.getPassword() == null) {
                    return new String[0];
                } else {
                    return new String[]{configuration.getPassword()};
                }
            }

        });

        // set the SO_TIMEOUT for the time after the connect phase
        if (configuration.getSoTimeout() > 0) {
            session.setTimeout(configuration.getSoTimeout());
        }

        // set proxy if configured
        if (proxy != null) {
            session.setProxy(proxy);
        }

        return session;
    }

    private static final class JSchLogger implements com.jcraft.jsch.Logger {

        private final LoggingLevel loggingLevel;

        private JSchLogger(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }

        public boolean isEnabled(int level) {
            switch (level) {
            case FATAL:
                // use ERROR as FATAL
                return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
            case ERROR:
                return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
            case WARN:
                return loggingLevel.isEnabled(LoggingLevel.WARN) && LOG.isWarnEnabled();
            case INFO:
                return loggingLevel.isEnabled(LoggingLevel.INFO) && LOG.isInfoEnabled();
            default:
                return loggingLevel.isEnabled(LoggingLevel.DEBUG) && LOG.isDebugEnabled();
            }
        }

        public void log(int level, String message) {
            switch (level) {
            case FATAL:
                // use ERROR as FATAL
                LOG.error("JSCH -> {}", message);
                break;
            case ERROR:
                LOG.error("JSCH -> {}", message);
                break;
            case WARN:
                LOG.warn("JSCH -> {}", message);
                break;
            case INFO:
                LOG.info("JSCH -> {}", message);
                break;
            default:
                LOG.debug("JSCH -> {}", message);
                break;
            }
        }
    }

    public synchronized boolean isConnected() throws GenericFileOperationFailedException {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }

    public synchronized void disconnect() throws GenericFileOperationFailedException {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }

    public synchronized void forceDisconnect() throws GenericFileOperationFailedException {
        try {
            if (session != null) {
                session.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
        } finally {
            // ensure these
            session = null;
            channel = null;
        }
    }

    private void reconnectIfNecessary() {
        if (!isConnected()) {
            connect(endpoint.getConfiguration());
        }
    }

    public synchronized boolean deleteFile(String name) throws GenericFileOperationFailedException {
        LOG.debug("Deleting file: {}", name);
        try {
            reconnectIfNecessary();
            channel.rm(name);
            return true;
        } catch (SftpException e) {
            LOG.debug("Cannot delete file: " + name, e);
            throw new GenericFileOperationFailedException("Cannot delete file: " + name, e);
        }
    }

    public synchronized boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        LOG.debug("Renaming file: {} to: {}", from, to);
        try {
            reconnectIfNecessary();
            //make use of the '/' separator because JSch expects this 
            // as the file separator even on Windows
            to = FileUtil.compactPath(to, '/');
            channel.rename(from, to);
            return true;
        } catch (SftpException e) {
            LOG.debug("Cannot rename file from: " + from + " to: " + to, e);
            throw new GenericFileOperationFailedException("Cannot rename file from: " + from + " to: " + to, e);
        }
    }

    public synchronized boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        // must normalize directory first
        directory = endpoint.getConfiguration().normalizePath(directory);

        LOG.trace("buildDirectory({},{})", directory, absolute);
        // ignore absolute as all dirs are relative with FTP
        boolean success = false;

        String originalDirectory = getCurrentDirectory();
        try {
            // maybe the full directory already exists
            try {
                channel.cd(directory);
                success = true;
            } catch (SftpException e) {
                // ignore, we could not change directory so try to create it instead
            }

            if (!success) {
                LOG.debug("Trying to build remote directory: {}", directory);

                try {
                    channel.mkdir(directory);
                    success = true;
                } catch (SftpException e) {
                    // we are here if the server side doesn't create intermediate folders
                    // so create the folder one by one
                    success = buildDirectoryChunks(directory);
                }
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot build directory: " + directory, e);
        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot build directory: " + directory, e);
        } finally {
            // change back to original directory
            if (originalDirectory != null) {
                changeCurrentDirectory(originalDirectory);
            }
        }

        return success;
    }

    private boolean buildDirectoryChunks(String dirName) throws IOException, SftpException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("/|\\\\");

        boolean success = false;
        boolean first = true;
        for (String dir : dirs) {
            if (first) {
                first = false;
            } else {
                sb.append('/');
            }
            sb.append(dir);

            // must normalize the directory name
            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            // do not try to build root folder (/ or \)
            if (!(directory.equals("/") || directory.equals("\\"))) {
                try {
                    LOG.trace("Trying to build remote directory by chunk: {}", directory);

                    channel.mkdir(directory);
                    success = true;
                } catch (SftpException e) {
                    // ignore keep trying to create the rest of the path
                }
            }
        }

        return success;
    }

    public synchronized String getCurrentDirectory() throws GenericFileOperationFailedException {
        LOG.trace("getCurrentDirectory()");
        try {
            String answer = channel.pwd();
            LOG.trace("Current dir: {}", answer);
            return answer;
        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot get current directory", e);
        }
    }

    public synchronized void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        LOG.trace("changeCurrentDirectory({})", path);
        if (ObjectHelper.isEmpty(path)) {
            return;
        }

        // must compact path so SFTP server can traverse correctly, make use of the '/'
        // separator because JSch expects this as the file separator even on Windows
        String before = path;
        char separatorChar = '/';
        path = FileUtil.compactPath(path, separatorChar);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Compacted path: {} -> {} using separator: {}", new Object[]{before, path, separatorChar});
        }

        // not stepwise should change directory in one operation
        if (!endpoint.getConfiguration().isStepwise()) {
            doChangeDirectory(path);
            return;
        }
        if (getCurrentDirectory().startsWith(path)) {
            // extract the path segment relative to the target path and make sure it keeps the preceding '/' for the regex op
            String p = getCurrentDirectory().substring(path.length() - (path.endsWith("/") ?  1 : 0));
            if (p.length() == 0) {
                return;
            }
            // the first character must be '/' and hence removed
            path = UP_DIR_PATTERN.matcher(p).replaceAll("/..").substring(1);
        }

        // if it starts with the root path then a little special handling for that
        if (FileUtil.hasLeadingSeparator(path)) {
            // change to root path
            doChangeDirectory(path.substring(0, 1));
            path = path.substring(1);
        }

        // split into multiple dirs
        final String[] dirs = path.split("/|\\\\");

        if (dirs == null || dirs.length == 0) {
            // path was just a relative single path
            doChangeDirectory(path);
            return;
        }

        // there are multiple dirs so do this in chunks
        for (String dir : dirs) {
            doChangeDirectory(dir);
        }
    }

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || ObjectHelper.isEmpty(path)) {
            return;
        }
        LOG.trace("Changing directory: {}", path);
        try {
            channel.cd(path);
        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot change directory to: " + path, e);
        }
    }

    public synchronized void changeToParentDirectory() throws GenericFileOperationFailedException {
        LOG.trace("changeToParentDirectory()");
        String current = getCurrentDirectory();

        String parent = FileUtil.compactPath(current + "/..");
        // must start with absolute
        if (!parent.startsWith("/")) {
            parent = "/" + parent;
        }

        changeCurrentDirectory(parent);
    }

    public synchronized List<SftpRemoteFile> listFiles() throws GenericFileOperationFailedException {
        return listFiles(".");
    }

    public synchronized List<SftpRemoteFile> listFiles(String path) throws GenericFileOperationFailedException {
        LOG.trace("listFiles({})", path);
        if (ObjectHelper.isEmpty(path)) {
            // list current directory if file path is not given
            path = ".";
        }

        try {
            final List<SftpRemoteFile> list = new ArrayList<>();

            @SuppressWarnings("rawtypes")
            Vector files = channel.ls(path);
            // can return either null or an empty list depending on FTP servers
            if (files != null) {
                for (Object file : files) {
                    list.add(new SftpRemoteFileJCraft((ChannelSftp.LsEntry) file));
                }
            }
            return list;
        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot list directory: " + path, e);
        }
    }

    public synchronized boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        LOG.trace("retrieveFile({})", name);
        if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
            // local work directory is configured so we should store file content as files in this local directory
            return retrieveFileToFileInLocalWorkDirectory(name, exchange);
        } else {
            // store file content directory as stream on the body
            return retrieveFileToStreamInBody(name, exchange);
        }
    }

    public synchronized void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        InputStream is = exchange.getIn().getHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, InputStream.class);

        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        OutputStream os = null;
        String currentDir = null;
        try {
            GenericFile<ChannelSftp.LsEntry> target =
                    (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            String remoteName = name;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed directory
                remoteName = FileUtil.stripPath(name);
            }

            // use input stream which works with Apache SSHD used for testing
            InputStream is = channel.get(remoteName);

            if (endpoint.getConfiguration().isStreamDownload()) {
                target.setBody(is);
                exchange.getIn().setHeader(RemoteFileComponent.REMOTE_FILE_INPUT_STREAM, is);
            } else {
                os = new ByteArrayOutputStream();
                target.setBody(os);
                IOHelper.copyAndCloseInput(is, os);
            }

            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        } finally {
            IOHelper.close(os, "retrieve: " + name, LOG);
            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange) throws GenericFileOperationFailedException {
        File temp;
        File local = new File(endpoint.getLocalWorkDirectory());
        OutputStream os;
        GenericFile<ChannelSftp.LsEntry> file =
                (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(file, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
        try {
            // use relative filename in local work directory
            String relativeName = file.getRelativeFilePath();

            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // create directory to local work file
            local.mkdirs();

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

            // store content as a file in the local work directory in the temp handle
            os = new FileOutputStream(temp);

            // set header with the path to the local work file
            exchange.getIn().setHeader(Exchange.FILE_LOCAL_WORK_PATH, local.getPath());
        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local);
        }
        String currentDir = null;
        try {
            // store the java.io.File handle as the body
            file.setBody(local);

            String remoteName = name;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed directory
                remoteName = FileUtil.stripPath(name);
            }

            channel.get(remoteName, os);

        } catch (SftpException e) {
            LOG.trace("Error occurred during retrieving file: {} to local directory. Deleting local work file: {}", name, temp);
            // failed to retrieve the file so we need to close streams and delete in progress file
            // must close stream before deleting file
            IOHelper.close(os, "retrieve: " + name, LOG);
            boolean deleted = FileUtil.deleteFile(temp);
            if (!deleted) {
                LOG.warn("Error occurred during retrieving file: " + name + " to local directory. Cannot delete local work file: " + temp);
            }
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        } finally {
            IOHelper.close(os, "retrieve: " + name, LOG);

            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
        }

        LOG.debug("Retrieve file to local work file result: true");

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

    public synchronized boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        // must normalize name first
        name = endpoint.getConfiguration().normalizePath(name);

        LOG.trace("storeFile({})", name);

        boolean answer = false;
        String currentDir = null;
        String path = FileUtil.onlyPath(name);
        String targetName = name;

        try {
            if (path != null && endpoint.getConfiguration().isStepwise()) {
                // must remember current dir so we stay in that directory after the write
                currentDir = getCurrentDirectory();

                // change to path of name
                changeCurrentDirectory(path);

                // the target name should be without path, as we have changed directory
                targetName = FileUtil.stripPath(name);
            }

            // store the file
            answer = doStoreFile(name, targetName, exchange);
        } finally {
            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
        }

        return answer;
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        LOG.trace("doStoreFile({})", targetName);

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore
                || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                doMoveExistingFile(name, targetName);
            }
        }

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
            LOG.debug("About to store file: {} using stream: {}", targetName, is);
            if (endpoint.getFileExist() == GenericFileExist.Append) {
                LOG.trace("Client appendFile: {}", targetName);
                channel.put(is, targetName, ChannelSftp.APPEND);
            } else {
                LOG.trace("Client storeFile: {}", targetName);
                // override is default
                channel.put(is, targetName);
            }
            if (LOG.isDebugEnabled()) {
                long time = watch.taken();
                LOG.debug("Took {} ({} millis) to store file: {} and FTP client returned: true",
                        new Object[]{TimeUtils.printDuration(time), time, targetName});
            }

            // after storing file, we may set chmod on the file
            String mode = endpoint.getConfiguration().getChmod();
            if (ObjectHelper.isNotEmpty(mode)) {
                // parse to int using 8bit mode
                int permissions = Integer.parseInt(mode, 8);
                LOG.trace("Setting chmod: {} on file: {}", mode, targetName);
                channel.chmod(permissions, targetName);
            }

            return true;

        } catch (SftpException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } catch (UnsupportedEncodingException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, LOG);
        }
    }

    /**
     * Moves any existing file due fileExists=Move is in use.
     */
    private void doMoveExistingFile(String name, String targetName) throws GenericFileOperationFailedException {
        // need to evaluate using a dummy and simulate the file first, to have access to all the file attributes
        // create a dummy exchange as Exchange is needed for expression evaluation
        // we support only the following 3 tokens.
        Exchange dummy = endpoint.createExchange();
        // we only support relative paths for the ftp component, so dont provide any parent
        String parent = null;
        String onlyName = FileUtil.stripPath(targetName);
        dummy.getIn().setHeader(Exchange.FILE_NAME, targetName);
        dummy.getIn().setHeader(Exchange.FILE_NAME_ONLY, onlyName);
        dummy.getIn().setHeader(Exchange.FILE_PARENT, parent);

        String to = endpoint.getMoveExisting().evaluate(dummy, String.class);
        // we only support relative paths for the ftp component, so strip any leading paths
        to = FileUtil.stripLeadingSeparator(to);
        // normalize accordingly to configuration
        to = endpoint.getConfiguration().normalizePath(to);
        if (ObjectHelper.isEmpty(to)) {
            throw new GenericFileOperationFailedException("moveExisting evaluated as empty String, cannot move existing file: " + name);
        }

        // do we have a sub directory
        String dir = FileUtil.onlyPath(to);
        if (dir != null) {
            // ensure directory exists
            buildDirectory(dir, false);
        }

        // deal if there already exists a file
        if (existsFile(to)) {
            if (endpoint.isEagerDeleteTargetFile()) {
                LOG.trace("Deleting existing file: {}", to);
                deleteFile(to);
            } else {
                throw new GenericFileOperationFailedException("Cannot moved existing file from: " + name + " to: " + to + " as there already exists a file: " + to);
            }
        }

        LOG.trace("Moving existing file: {} to: {}", name, to);
        if (!renameFile(targetName, to)) {
            throw new GenericFileOperationFailedException("Cannot rename file from: " + name + " to: " + to);
        }
    }

    public synchronized boolean existsFile(String name) throws GenericFileOperationFailedException {
        LOG.trace("existsFile({})", name);
        if (endpoint.isFastExistsCheck()) {
            return fastExistsFile(name);
        }
        // check whether a file already exists
        String directory = FileUtil.onlyPath(name);
        if (directory == null) {
            // assume current dir if no path could be extracted
            directory = ".";
        }
        String onlyName = FileUtil.stripPath(name);

        try {
            @SuppressWarnings("rawtypes")
            Vector files = channel.ls(directory);
            // can return either null or an empty list depending on FTP servers
            if (files == null) {
                return false;
            }
            for (Object file : files) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) file;
                String existing = entry.getFilename();
                LOG.trace("Existing file: {}, target file: {}", existing, name);
                existing = FileUtil.stripPath(existing);
                if (existing != null && existing.equals(onlyName)) {
                    return true;
                }
            }
            return false;
        } catch (SftpException e) {
            // or an exception can be thrown with id 2 which means file does not exists
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                return false;
            }
            // otherwise its a more serious error so rethrow
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
    }

    protected boolean fastExistsFile(String name) throws GenericFileOperationFailedException {
        LOG.trace("fastExistsFile({})", name);
        try {
            @SuppressWarnings("rawtypes")
            Vector files = channel.ls(name);
            if (files == null) {
                return false;
            }
            return files.size() >= 1;
        } catch (SftpException e) {
            // or an exception can be thrown with id 2 which means file does not exists
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                return false;
            }
            // otherwise its a more serious error so rethrow
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }

    }

    public synchronized boolean sendNoop() throws GenericFileOperationFailedException {
        if (isConnected()) {
            try {
                session.sendKeepAliveMsg();
                return true;
            } catch (Exception e) {
                LOG.debug("SFTP session was closed. Ignoring this exception.", e);
                return false;
            }
        }
        return false;
    }

    public synchronized boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        // is not implemented
        return true;
    }
}
