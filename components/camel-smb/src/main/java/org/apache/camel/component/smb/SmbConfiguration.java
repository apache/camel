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

import com.hierynomus.smbj.SmbConfig;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

@UriParams
public class SmbConfiguration {

    protected static final int DEFAULT_IDEMPOTENT_CACHE_SIZE = 1000;

    @UriParam(description = "The path, within the share, to consume the files from")
    private String path;

    @UriParam(label = "producer", description = "What action to take if the SMB file already exists",
              defaultValue = "Ignore", enums = "Override,Append,Fail,Ignore,Move,TryRename")
    private GenericFileExist fileExist;
    @Metadata(defaultValue = "false")
    @UriParam(label = "producer", description = "Whether to create parent directory if it does not exist",
              defaultValue = "false")
    private boolean autoCreate;
    @Metadata(defaultValue = "2048")
    @UriParam(label = "producer", description = "Read buffer size when for file being produced", defaultValue = "2048")
    private int readBufferSize;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether or not to disconnect from remote SMB server right after use. Disconnect will only disconnect the current connection to the SMB server. If you have a consumer which you want to stop, then you need to stop the consumer route instead.")
    protected boolean disconnect;

    @UriParam(defaultValue = "*.txt", description = "The search pattern used to list the files")
    private String searchPattern;
    @UriParam(label = "security", description = "The username required to access the share", secret = true)
    private String username;
    @UriParam(label = "security", description = "The password to access the share", secret = true)
    private String password;
    @UriParam(label = "security", description = "The user domain")
    private String domain;
    @UriParam(label = "advanced",
              description = "An optional SMB I/O bean to use to setup the file access attributes when reading/writing a file")
    private SmbIOBean smbIoBean = new SmbReadBean();
    @UriParam(label = "advanced", description = "A pluggable repository org.apache.camel.spi.IdempotentRepository "
                                                + "which by default use MemoryIdempotentRepository if none is specified.")
    protected IdempotentRepository idempotentRepository
            = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
    @Metadata(autowired = true)
    @UriParam(label = "advanced",
              description = "An optional SMB client configuration, can be used to configure client specific "
                            + " configurations, like timeouts")
    private SmbConfig smbConfig;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "If a directory, will look for files in all the sub-directories as well.")
    protected boolean recursive;

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

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public void setRecursive(String recursiveString) {
        this.recursive = Boolean.valueOf(recursiveString);
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public SmbIOBean getSmbIoBean() {
        return smbIoBean;
    }

    public void setSmbIoBean(SmbIOBean smbIoBean) {
        this.smbIoBean = smbIoBean;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public SmbConfig getSmbConfig() {
        return smbConfig;
    }

    public void setSmbConfig(SmbConfig smbConfig) {
        this.smbConfig = smbConfig;
    }

    public GenericFileExist getFileExist() {
        return fileExist;
    }

    public void setFileExist(GenericFileExist fileExist) {
        this.fileExist = fileExist;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(String autoCreate) {
        this.autoCreate = Boolean.valueOf(autoCreate);
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }
}
