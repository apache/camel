package org.apache.camel.component.dropbox.util;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/19/13
 * Time: 5:16 PM
 * To change this template use File | Settings | File Templates.
 */
public final class DropboxConstants {

    //not instantiate
    private DropboxConstants() {}

    public static final String DROPBOX_FILE_SEPARATOR = "/";
    public static final long POLL_CONSUMER_DELAY = 60 * 60 * 1000L;
    public static final String RESULT_OP_CODE = "RESULT_OP_CODE";
    public static final String UPLOADED_FILE = "UPLOADED_FILE";
    public static final String DOWNLOADED_FILE = "DOWNLOADED_FILE";
    public static final String ENTRIES_SIZE = "ENTRIES_SIZE";
    public static final String ENTRIES = "ENTRIES";
}
