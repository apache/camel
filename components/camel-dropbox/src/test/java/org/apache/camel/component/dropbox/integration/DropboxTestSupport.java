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
package org.apache.camel.component.dropbox.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropboxTestSupport extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DropboxTestSupport.class);

    protected final Properties properties;
    protected String workdir;
    protected String token;
    protected String apiKey;
    protected String apiSecret;
    protected String refreshToken;
    protected Long expireIn;

    private final DbxClientV2 client;

    protected DropboxTestSupport() {
        properties = loadProperties();

        workdir = properties.getProperty("workDir");
        token = properties.getProperty("accessToken");
        refreshToken = properties.getProperty("refreshToken");
        apiKey = properties.getProperty("apiKey");
        apiSecret = properties.getProperty("apiSecret");
        expireIn = Long.valueOf(properties.getProperty("expireIn"));

        DbxRequestConfig config = DbxRequestConfig.newBuilder(properties.getProperty("clientIdentifier")).build();
        DbxCredential credential = new DbxCredential(token, expireIn, refreshToken, apiKey, apiSecret);
        client = new DbxClientV2(config, credential);

    }

    private static Properties loadProperties() {
        final Properties properties = new Properties();
        try (InputStream inStream = DropboxTestSupport.class.getResourceAsStream("/test-options.properties")) {
            properties.load(inStream);
        } catch (IOException e) {
            LOG.error("I/O error: reading test-options.properties: {}", e.getMessage(), e);
            throw new IllegalAccessError("test-options.properties could not be found");
        }
        return properties;
    }

    // Used by JUnit to automatically trigger the integration tests
    @SuppressWarnings("unused")
    private static boolean hasCredentials() {
        Properties properties = loadProperties();

        return !properties.getProperty("accessToken", "").isEmpty();
    }

    @BeforeEach
    public void setUpWorkingFolder() throws DbxException {
        createDir(workdir);
    }

    protected void createDir(String name) throws DbxException {
        try {
            removeDir(name);
        } finally {
            client.files().createFolderV2(name);
        }
    }

    protected void removeDir(String name) throws DbxException {
        client.files().deleteV2(name);
    }

    protected void createFile(String fileName, String content) throws IOException {
        try {
            client.files().uploadBuilder(workdir + "/" + fileName)
                    .uploadAndFinish(new ByteArrayInputStream(content.getBytes()));
            //wait some time for synchronization
            Thread.sleep(1000);
        } catch (DbxException e) {
            LOG.info("folder is already created");
        } catch (InterruptedException e) {
            LOG.debug("Waiting for synchronization interrupted.");
        }
    }

    protected String getFileContent(String path) throws DbxException, IOException {
        try (ByteArrayOutputStream target = new ByteArrayOutputStream();
             DbxDownloader<FileMetadata> downloadedFile = client.files().download(path)) {
            if (downloadedFile != null) {
                downloadedFile.download(target);
            }
            return target.toString();
        }
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
