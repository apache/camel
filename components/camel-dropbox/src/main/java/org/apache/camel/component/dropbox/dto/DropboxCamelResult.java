package org.apache.camel.component.dropbox.dto;

import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.util.DropboxResultOpCode;

import static org.apache.camel.component.dropbox.util.DropboxConstants.RESULT_OP_CODE;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/20/13
 * Time: 7:35 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DropboxCamelResult {

    protected Object[] dropboxObjs;

    public abstract void populateExchange(Exchange exchange);
    public void createResultOpCode(Exchange exchange,String code) {
        exchange.getIn().setHeader(RESULT_OP_CODE, code);
    }

    public Object[] getDropboxObjs() {
        return dropboxObjs;
    }

    public void setDropboxObjs(Object... dropboxObjs) {
        this.dropboxObjs = dropboxObjs;
    }
}
