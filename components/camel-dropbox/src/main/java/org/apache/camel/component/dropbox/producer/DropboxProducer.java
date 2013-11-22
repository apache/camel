package org.apache.camel.component.dropbox.producer;

import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/20/13
 * Time: 5:52 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DropboxProducer extends DefaultProducer {

    protected DropboxEndpoint endpoint;
    protected DropboxConfiguration configuration;

    public DropboxProducer(DropboxEndpoint endpoint, DropboxConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }
}
