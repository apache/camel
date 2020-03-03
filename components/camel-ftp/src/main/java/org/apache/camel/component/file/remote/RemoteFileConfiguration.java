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

import java.net.URI;

import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Configuration of the FTP server
 */
public abstract class RemoteFileConfiguration extends GenericFileConfiguration {

    /**
     * Path separator as either unix or windows style.
     * <p/>
     * UNIX = Path separator / is used Windows = Path separator \ is used Auto =
     * Use existing path separator in file name
     */
    public enum PathSeparator {
        UNIX, Windows, Auto
    }

    // component name is implied as the protocol, eg ftp/ftps etc
    private String protocol;
    @UriPath(description = "Hostname of the FTP server")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "Port of the FTP server")
    private int port;
    @UriPath(name = "directoryName", description = "The starting directory")
    private String directoryName;
    @UriParam(label = "security", secret = true, description = "Username to use for login")
    private String username;
    @UriParam(label = "security", secret = true, description = "Password to use for login")
    private String password;
    @UriParam(description = "Specifies the file transfer mode, BINARY or ASCII. Default is ASCII (false).")
    private boolean binary;
    @UriParam(description = "Sets passive mode connections.<br/> Default is active mode connections.")
    private boolean passiveMode;
    @UriParam(defaultValue = "10000", label = "advanced", description = "Sets the connect timeout for waiting "
                                                                        + "for a connection to be established <p/> Used by both FTPClient and JSCH")
    private int connectTimeout = 10000;
    @UriParam(defaultValue = "30000", label = "advanced", description = "Sets the data timeout for waiting for " + "reply <p/> Used only by FTPClient")
    private int timeout = 30000;
    @UriParam(defaultValue = "300000", label = "advanced", description = "Sets the so timeout <p/> FTP and FTPS "
                                                                         + "Only for Camel 2.4. SFTP for Camel 2.14.3/2.15.3/2.16 onwards. Is the SocketOptions.SO_TIMEOUT value "
                                                                         + "in millis. Recommended option is to set this to 300000 so as not have a hanged connection. On SFTP this "
                                                                         + "option is set as timeout on the JSCH Session instance.")
    private int soTimeout = 300000;
    @UriParam(label = "advanced", description = "Should an exception be thrown if connection failed (exhausted) <p/> "
                                                + "By default exception is not thrown and a <tt>WARN</tt> is logged. You can use this to enable exception "
                                                + "being thrown and handle the thrown exception from the {@link "
                                                + "org.apache.camel.spi.PollingConsumerPollStrategy} rollback method.")
    private boolean throwExceptionOnConnectFailed;
    @UriParam(label = "advanced", description = "Sets optional site command(s) to be executed after successful "
                                                + "login. <p/> Multiple site commands can be separated using a new line character.")
    private String siteCommand;
    @UriParam(defaultValue = "true", label = "advanced", description = "Sets whether we should stepwise change "
                                                                       + "directories while traversing file structures when downloading files, or as well when uploading a file "
                                                                       + "to a directory. <p/> You can disable this if you for example are in a situation where you cannot change "
                                                                       + "directory on the FTP server due security reasons. "
                                                                       + "Stepwise cannot be used together with streamDownload.")
    private boolean stepwise = true;
    @UriParam(defaultValue = "UNIX", description = "Sets the path separator to be used. <p/> UNIX = Uses unix style "
                                                   + "path separator Windows = Uses windows style path separator Auto = (is default) Use existing path " + "separator in file name")
    private PathSeparator separator = PathSeparator.UNIX;
    @UriParam(label = "consumer", description = "Sets the download method to use when not using a local working "
                                                + "directory.  If set to true, the remote files are streamed to the route as they are read.  When set to "
                                                + "false, the remote files are loaded into memory before being sent into the route. "
                                                + "If enabling this option then you must set stepwise=false as both cannot be enabled at the same time.")
    private boolean streamDownload;
    @UriParam(defaultValue = "true", label = "consumer,advanced", description = "Whether to allow using LIST "
                                                                                + "command when downloading a file. <p/> Default is <tt>true</tt>. In some use cases you may want to "
                                                                                + "download a specific file and are not allowed to use the LIST command, and therefore you can set "
                                                                                + "this option to <tt>false</tt>. Notice when using this option, then the specific file to download "
                                                                                + "does <b>not</b> include meta-data information such as file size, timestamp, permissions etc, because "
                                                                                + "those information is only possible to retrieve when LIST command is in use.")
    private boolean useList = true;
    @UriParam(label = "consumer,advanced", description = "Whether to ignore when (trying to list files in "
                                                         + "directories or when downloading a file), which does not exist or due to permission error. <p/> "
                                                         + "By default when a directory or file does not exists or insufficient permission, then an exception "
                                                         + "is thrown. Setting this option to <tt>true</tt> allows to ignore that instead.")
    private boolean ignoreFileNotFoundOrPermissionError;
    @UriParam(label = "producer,advanced", defaultValue = "true", description = "Whether to send a noop command "
                                                                                + "as a pre-write check before uploading files to the FTP server. <p/> This is enabled by default as "
                                                                                + "a validation of the connection is still valid, which allows to silently re-connect to be able to "
                                                                                + "upload the file. However if this causes problems, you can turn this option off.")
    private boolean sendNoop = true;

    public RemoteFileConfiguration() {
    }

    public RemoteFileConfiguration(URI uri) {
        configure(uri);
    }

    @Override
    public boolean needToNormalize() {
        return false;
    }

    @Override
    public void configure(URI uri) {
        super.configure(uri);
        // after configure the directory has been resolved, so we can use it for
        // directoryName
        // (directoryName is the name we use in the other file components, to
        // use consistent name)
        setDirectoryName(getDirectory());
        setProtocol(uri.getScheme());
        setDefaultPort();

        // UserInfo can contain both username and password as:
        // user:pwd@ftpserver
        // see: http://en.wikipedia.org/wiki/URI_scheme
        String username = uri.getUserInfo();
        String pw = null;
        if (username != null && username.contains(":")) {
            pw = StringHelper.after(username, ":");
            username = StringHelper.before(username, ":");
        }
        if (username != null) {
            setUsername(username);
        }
        if (pw != null) {
            setPassword(pw);
        }

        setHost(uri.getHost());
        setPort(uri.getPort());
    }

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return protocol + "://" + (username != null ? username : "anonymous") + "@" + host + ":" + getPort();
    }

    protected abstract void setDefaultPort();

    public String getHost() {
        return host;
    }

    /**
     * Hostname of the FTP server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port of the FTP server
     */
    public void setPort(int port) {
        // only set port if provided with a positive number
        if (port > 0) {
            this.port = port;
        }
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to use for login
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The ftp protocol to use
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username to use for login
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * The starting directory
     */
    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    public boolean isBinary() {
        return binary;
    }

    /**
     * Specifies the file transfer mode, BINARY or ASCII. Default is ASCII
     * (false).
     */
    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    public boolean isPassiveMode() {
        return passiveMode;
    }

    /**
     * Sets passive mode connections. <br/>
     * Default is active mode connections.
     */
    public void setPassiveMode(boolean passiveMode) {
        this.passiveMode = passiveMode;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the connect timeout for waiting for a connection to be established
     * <p/>
     * Used by both FTPClient and JSCH
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the data timeout for waiting for reply
     * <p/>
     * Used only by FTPClient
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * Sets the so timeout
     * <p/>
     * FTP and FTPS Only for Camel 2.4. SFTP for Camel 2.14.3/2.15.3/2.16
     * onwards. Is the SocketOptions.SO_TIMEOUT value in millis. Recommended
     * option is to set this to 300000 so as not have a hanged connection. On
     * SFTP this option is set as timeout on the JSCH Session instance.
     */
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public boolean isThrowExceptionOnConnectFailed() {
        return throwExceptionOnConnectFailed;
    }

    /**
     * Should an exception be thrown if connection failed (exhausted)
     * <p/>
     * By default exception is not thrown and a <tt>WARN</tt> is logged. You can
     * use this to enable exception being thrown and handle the thrown exception
     * from the {@link org.apache.camel.spi.PollingConsumerPollStrategy}
     * rollback method.
     */
    public void setThrowExceptionOnConnectFailed(boolean throwExceptionOnConnectFailed) {
        this.throwExceptionOnConnectFailed = throwExceptionOnConnectFailed;
    }

    public String getSiteCommand() {
        return siteCommand;
    }

    /**
     * Sets optional site command(s) to be executed after successful login.
     * <p/>
     * Multiple site commands can be separated using a new line character.
     */
    public void setSiteCommand(String siteCommand) {
        this.siteCommand = siteCommand;
    }

    public boolean isStepwise() {
        return stepwise;
    }

    /**
     * Sets whether we should stepwise change directories while traversing file
     * structures when downloading files, or as well when uploading a file to a
     * directory.
     * <p/>
     * You can disable this if you for example are in a situation where you
     * cannot change directory on the FTP server due security reasons.
     *
     * @param stepwise whether to use change directory or not
     */
    public void setStepwise(boolean stepwise) {
        this.stepwise = stepwise;
    }

    public PathSeparator getSeparator() {
        return separator;
    }

    /**
     * Sets the path separator to be used.
     * <p/>
     * UNIX = Uses unix style path separator Windows = Uses windows style path
     * separator Auto = (is default) Use existing path separator in file name
     */
    public void setSeparator(PathSeparator separator) {
        this.separator = separator;
    }

    public boolean isStreamDownload() {
        return streamDownload;
    }

    /**
     * Sets the download method to use when not using a local working directory.
     * If set to true, the remote files are streamed to the route as they are
     * read. When set to false, the remote files are loaded into memory before
     * being sent into the route.
     */
    public void setStreamDownload(boolean streamDownload) {
        this.streamDownload = streamDownload;
    }

    public boolean isUseList() {
        return useList;
    }

    /**
     * Whether to allow using LIST command when downloading a file.
     * <p/>
     * Default is <tt>true</tt>. In some use cases you may want to download a
     * specific file and are not allowed to use the LIST command, and therefore
     * you can set this option to <tt>false</tt>. Notice when using this option,
     * then the specific file to download does <b>not</b> include meta-data
     * information such as file size, timestamp, permissions etc, because those
     * information is only possible to retrieve when LIST command is in use.
     */
    public void setUseList(boolean useList) {
        this.useList = useList;
    }

    public boolean isIgnoreFileNotFoundOrPermissionError() {
        return ignoreFileNotFoundOrPermissionError;
    }

    /**
     * Whether to ignore when (trying to list files in directories or when
     * downloading a file), which does not exist or due to permission error.
     * <p/>
     * By default when a directory or file does not exists or insufficient
     * permission, then an exception is thrown. Setting this option to
     * <tt>true</tt> allows to ignore that instead.
     */
    public void setIgnoreFileNotFoundOrPermissionError(boolean ignoreFileNotFoundOrPermissionError) {
        this.ignoreFileNotFoundOrPermissionError = ignoreFileNotFoundOrPermissionError;
    }

    public boolean isSendNoop() {
        return sendNoop;
    }

    /**
     * Whether to send a noop command as a pre-write check before uploading
     * files to the FTP server.
     * <p/>
     * This is enabled by default as a validation of the connection is still
     * valid, which allows to silently re-connect to be able to upload the file.
     * However if this causes problems, you can turn this option off.
     */
    public void setSendNoop(boolean sendNoop) {
        this.sendNoop = sendNoop;
    }

    /**
     * Normalizes the given path according to the configured path separator.
     *
     * @param path the given path
     * @return the normalized path
     */
    public String normalizePath(String path) {
        if (ObjectHelper.isEmpty(path) || separator == PathSeparator.Auto) {
            return path;
        }

        if (separator == PathSeparator.UNIX) {
            // unix style
            return path.replace('\\', '/');
        } else {
            // windows style
            return path.replace('/', '\\');
        }
    }
}
