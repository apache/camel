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

import java.net.URI;

import com.hierynomus.smbj.SmbConfig;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.FileUtil;

@UriParams
public class SmbConfiguration extends GenericFileConfiguration {

    // component name is implied as the protocol
    private String protocol;

    @UriPath
    @Metadata(required = true)
    private String hostname;
    @UriPath(defaultValue = "445")
    private int port = 445;
    @UriPath(description = "The name of the share directory")
    @Metadata(required = true)
    private String shareName;
    @UriParam(label = "security", description = "The username required to access the share", secret = true)
    private String username;
    @UriParam(label = "security", description = "The password to access the share", secret = true)
    private String password;
    @UriParam(label = "security", description = "The user domain")
    private String domain;
    @UriParam(label = "consumer", description = "The path, within the share, to consume the files from")
    private String path;
    @UriParam(label = "consumer",
              description = "The search pattern used to list the files (server side on SMB). This parameter can contain the name of a file (or multiple files, if wildcards are used) within this directory. When it is null all files are included."
                            + " Two wild card characters are supported in the search pattern. The ? (question mark) character matches a single character. If a search pattern contains one or more ? characters, then exactly that number of characters is matched by the wildcards."
                            + " For example, the criterion ??x matches abx but not abcx or ax, because the two file names do not have enough characters preceding the literal. When a file name criterion has ? characters trailing a literal, then the match is made with specified number of characters or less."
                            + " For example, the criterion x?? matches xab, xa, and x, but not xabc. If only ? characters are present in the file name selection criterion, then the match is made as if the criterion contained ? characters trailing a literal."
                            + " The * (asterisk) character matches an entire file name. A null or empty specification criterion also selects all file names. For example, *.abc or .abc match any file with an extension of abc. *.*, *, or empty string match all files in a directory.")
    private String searchPattern;
    @UriParam(label = "consumer", description = "Sets the download method to use when not using a local working directory."
                                                + " If set to true, the remote files are streamed to the route as they are read. When set to"
                                                + " false, the remote files are loaded into memory before being sent into the route.")
    @Metadata
    private boolean streamDownload;
    @UriParam(label = "consumer,advanced", description = "Whether the SMB consumer should download the file. If this "
                                                         + "option is set to false, then the message body will be null, but the consumer will still trigger a Camel "
                                                         + "Exchange that has details about the file such as file name, file size, etc. It's just that the file will "
                                                         + "not be downloaded.")
    private boolean download = true;
    @UriParam(label = "common", description = "Whether or not to disconnect from remote SMB share right after use. "
                                              + "Disconnect will only disconnect the current connection to the SMB share. If you have a consumer which "
                                              + "you want to stop, then you need to stop the consumer/route instead.")
    private boolean disconnect;
    @UriParam(label = "producer,advanced", description = "Whether or not to disconnect from remote SMB share right "
                                                         + "after a Batch upload is complete. disconnectOnBatchComplete will only disconnect the current connection "
                                                         + "to the SMB share.")
    private boolean disconnectOnBatchComplete;
    @Metadata(autowired = true)
    @UriParam(label = "advanced",
              description = "An optional SMB client configuration, can be used to configure client specific "
                            + " configurations, like timeouts")
    private SmbConfig smbConfig;

    public SmbConfiguration() {
        setProtocol("smb");
    }

    public SmbConfiguration(URI uri) {
        super.setDirectory("");
        setProtocol(uri.getScheme());
        setHostname(uri.getHost());
        if (uri.getPort() > 0) {
            setPort(uri.getPort());
        }
        setShareName(FileUtil.stripLeadingSeparator(uri.getPath()));
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The smb protocol to use
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
        super.setDirectory(path);
    }

    @Override
    public String getDirectory() {
        return super.getDirectory() == null ? "" : super.getDirectory();
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The share hostname or IP address
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    /**
     * The share port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getShareName() {
        return shareName;
    }

    /**
     * The name of the share to connect to.
     */
    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public boolean isStreamDownload() {
        return streamDownload;
    }

    public void setStreamDownload(boolean streamDownload) {
        this.streamDownload = streamDownload;
    }

    public boolean isDownload() {
        return download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isDisconnectOnBatchComplete() {
        return disconnectOnBatchComplete;
    }

    public void setDisconnectOnBatchComplete(boolean disconnectOnBatchComplete) {
        this.disconnectOnBatchComplete = disconnectOnBatchComplete;
    }

    public SmbConfig getSmbConfig() {
        return smbConfig;
    }

    public void setSmbConfig(SmbConfig smbConfig) {
        this.smbConfig = smbConfig;
    }
}
