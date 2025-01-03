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
    @Metadata(defaultValue = "2048")
    @UriParam(label = "producer", description = "Read buffer size when for file being produced", defaultValue = "2048")
    private int readBufferSize;
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

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public SmbConfig getSmbConfig() {
        return smbConfig;
    }

    public void setSmbConfig(SmbConfig smbConfig) {
        this.smbConfig = smbConfig;
    }
}
