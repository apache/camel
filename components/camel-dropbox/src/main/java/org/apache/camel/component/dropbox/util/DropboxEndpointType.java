package org.apache.camel.component.dropbox.util;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/20/13
 * Time: 3:37 PM
 * To change this template use File | Settings | File Templates.
 */
public enum DropboxEndpointType {
    polling("polling"),
    del("del"),
    search("search"),
    get("get"),
    move("move");

    private DropboxEndpointType(final String text) {
        this.text = text;
    }

    private final String text;

    @Override
    public String toString() {
        return text;
    }

}

