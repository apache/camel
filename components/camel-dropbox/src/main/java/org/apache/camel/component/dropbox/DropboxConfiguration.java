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
import org.apache.camel.component.dropbox.util.DropboxOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class DropboxConfiguration {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxConfiguration.class);


    /*
     Dropbox auth
     */
    private String appKey;
    private String appSecret;
    private String accessToken;
    private String localPath;
    private String remotePath;
    private String newRemotePath;
    private String query;
    //operation supported
    private DropboxOperation operation;
    //reference to dropboxclient
    private DbxClient client;

    public DbxClient getClient() {
        return client;
    }

    public void createClient() {
        /*TODO clientIdentifier*/
        String clientIdentifier = "camel-dropbox/1.0";

        DbxAppInfo appInfo = new DbxAppInfo(appKey, appSecret);
        DbxRequestConfig config =
                new DbxRequestConfig(clientIdentifier, Locale.getDefault().toString());
        DbxClient client = new DbxClient(config, accessToken);
        //TODO define custom exception
        if(client == null) {
            throw new IllegalStateException("can't establish a Dropbox conenction!");
        }
        this.client = client;

    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
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


    public DropboxOperation getOperation() {
        return operation;
    }

    public void setOperation(DropboxOperation operation) {
        this.operation = operation;
    }

}
