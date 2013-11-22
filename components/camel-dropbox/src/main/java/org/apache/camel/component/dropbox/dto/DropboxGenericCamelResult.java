package org.apache.camel.component.dropbox.dto;

import org.apache.camel.Exchange;

import static org.apache.camel.component.dropbox.util.DropboxConstants.UPLOADED_FILE;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/20/13
 * Time: 7:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class DropboxGenericCamelResult extends DropboxCamelResult {
    @Override
    public void populateExchange(Exchange exchange) {
       throw new UnsupportedOperationException("Method not implemented!");
    }
}
