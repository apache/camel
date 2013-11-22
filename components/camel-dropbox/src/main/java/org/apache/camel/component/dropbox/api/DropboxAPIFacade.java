package org.apache.camel.component.dropbox.api;

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

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/20/13
 * Time: 3:32 PM
 * To change this template use File | Settings | File Templates.
 */
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
