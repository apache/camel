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
package org.apache.camel.component.dropbox;

import com.dropbox.core.*;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxOperation;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class DropboxConfiguration {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxConfiguration.class);

    //dropbox auth options
    private String accessToken;
    //local path to put files
    private String localPath;
    //where to put files on dropbox
    private String remotePath;
    //new path on dropbox when moving files
    private String newRemotePath;
    //search query on dropbox
    private String query;
    //in case of uploading if force or add existing file
    private DropboxUploadMode uploadMode;
    //id of the app
    private String clientIdentifier;
    //specific dropbox operation for the component
    private DropboxOperation operation;
    //reference to dropboxclient
    private DbxClient client;

    public DbxClient getClient() {
        return client;
    }

    public void createClient() throws DropboxException {
        DbxRequestConfig config =
                new DbxRequestConfig(clientIdentifier, Locale.getDefault().toString());
        DbxClient client = new DbxClient(config, accessToken);
        if(client == null) {
            throw new DropboxException("can't establish a Dropbox conenction!");
        }
        this.client = client;

    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public String getNewRemotePath() {
        return newRemotePath;
    }

    public void setNewRemotePath(String newRemotePath) {
        this.newRemotePath = newRemotePath;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getClientIdentifier() {
        return clientIdentifier;
    }

    public void setClientIdentifier(String clientIdentifier) {
        this.clientIdentifier = clientIdentifier;
    }

    public DropboxOperation getOperation() {
        return operation;
    }

    public void setOperation(DropboxOperation operation) {
        this.operation = operation;
    }

    public DropboxUploadMode getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(DropboxUploadMode uploadMode) {
        this.uploadMode = uploadMode;
    }

}
