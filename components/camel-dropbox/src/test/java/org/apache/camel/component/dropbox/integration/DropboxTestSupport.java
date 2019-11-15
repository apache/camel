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
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;

public class DropboxTestSupport extends CamelTestSupport {


    protected final Properties properties;
    protected String workdir;
    protected String token;
    private DbxClientV2 client;

    protected DropboxTestSupport() {
        properties = new Properties();
        try (InputStream inStream = getClass().getResourceAsStream("/test-options.properties")) {
            properties.load(inStream);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalAccessError("test-options.properties could not be found");
        }

        workdir = properties.getProperty("workDir");
        token = properties.getProperty("accessToken");

        DbxRequestConfig config = DbxRequestConfig.newBuilder(properties.getProperty("clientIdentifier")).build();
        client = new DbxClientV2(config, token);

    }

    @Before
    public void setUpWorkingFolder() throws DbxException {
        createDir(workdir);
    }

    protected void createDir(String name) throws DbxException {
        try {
            removeDir(name);
        } finally {
            client.files().createFolder(name);
        }
    }

    protected void removeDir(String name) throws DbxException {
        client.files().delete(name);
    }

    protected void createFile(String fileName, String content) throws IOException {
        try {
            client.files().uploadBuilder(workdir + "/" + fileName).uploadAndFinish(new ByteArrayInputStream(content.getBytes()));
            //wait some time for synchronization
            Thread.sleep(1000);
        } catch (DbxException e) {
            log.info("folder is already created");
        } catch (InterruptedException e) {
            log.debug("Waiting for synchronization interrupted.");
        }
    }

    protected String getFileContent(String path) throws DbxException, IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        DbxDownloader<FileMetadata> downloadedFile = client.files().download(path);
        if (downloadedFile != null) {
            downloadedFile.download(target);
        }
        return new String(target.toByteArray());
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return properties;
    }

}
