package org.apache.camel.component.dropbox;

import com.dropbox.core.*;
import org.apache.camel.component.dropbox.util.DropboxOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/18/13
 * Time: 10:19 PM
 * To change this template use File | Settings | File Templates.
 */
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
        /*TODO clientIdentifier
        according to the dropbox API doc:
        If you're the author a higher-level library on top of the basic SDK,
        and the "Photo Edit" Android app is using your library to access Dropbox,
        you should append your library's name and version to form the full identifier.
        For example, if your library is called "File Picker",
        you might set this field to: "PhotoEditAndroid/2.4 FilePicker/0.1-beta"
         */
        String clientIdentifier = "camel-dropbox/1.0";

        DbxAppInfo appInfo = new DbxAppInfo(appKey, appSecret);
        DbxRequestConfig config =
                new DbxRequestConfig(clientIdentifier, Locale.getDefault().toString());
        DbxClient client = new DbxClient(config, accessToken);
        //TODO define custom exception
        if(client == null) {
            throw new IllegalStateException("cant establish Dropbox conenction!");
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
