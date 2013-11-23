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
package org.apache.camel.component.dropbox.core;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxWriteMode;
import org.apache.camel.component.dropbox.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dropbox.util.DropboxConstants.DROPBOX_FILE_SEPARATOR;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class DropboxAPIFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxAPIFacade.class);

    private static DropboxAPIFacade instance;
    private static DbxClient client;

    private DropboxAPIFacade(){}

    public static DropboxAPIFacade getInstance(DbxClient client) {
        if (instance == null) {
            instance = new DropboxAPIFacade();
            instance.client = client;
        }
        return instance;
    }

    public DropboxCamelResult putSingleFile(String localPath) throws Exception {
        File inputFile = new File(localPath);
        FileInputStream inputStream = new FileInputStream(inputFile);
        DbxEntry.File uploadedFile = null;
        DropboxCamelResult result = null;
        try {
            uploadedFile =
                    instance.client.uploadFile(DROPBOX_FILE_SEPARATOR+localPath,
                            DbxWriteMode.add(), inputFile.length(), inputStream);
            result = new DropboxFileUploadCamelResult();
            result.setDropboxObjs(uploadedFile);
            return result;
        }
        finally {
            inputStream.close();
        }

    }

    public DropboxCamelResult search(String remotePath,String query) throws Exception {
        DropboxCamelResult result = null;
        DbxEntry.WithChildren listing = null;
        if(query == null) {
            listing = instance.client.getMetadataWithChildren(remotePath);
            result = new DropboxSearchCamelResult();
            result.setDropboxObjs(listing.children);
        }
        else {
            LOG.info("search by query:"+query);
            List<DbxEntry> entries = instance.client.searchFileAndFolderNames(remotePath,query);
            result = new DropboxSearchCamelResult();
            result.setDropboxObjs(entries);
        }
        return result;
    }

    public DropboxCamelResult del(String remotePath) throws Exception {
        DropboxCamelResult result = null;
        instance.client.delete(remotePath);
        result = new DropboxGenericCamelResult();
        return result;
    }

    public DropboxCamelResult move(String remotePath,String newRemotePath) throws Exception {
        DropboxCamelResult result = null;
        instance.client.move(remotePath, newRemotePath);
        result = new DropboxGenericCamelResult();
        return result;
    }

    public DropboxCamelResult get(String remotePath) throws Exception {
        DropboxCamelResult result = null;
        //create a baos
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DbxEntry.File downloadedFile = instance.client.getFile(remotePath,null,baos);
        result = new DropboxFileDownloadCamelResult();
        result.setDropboxObjs(remotePath,baos);
        LOG.info("downloaded baos size:"+baos.size());
        return result;
    }


}
