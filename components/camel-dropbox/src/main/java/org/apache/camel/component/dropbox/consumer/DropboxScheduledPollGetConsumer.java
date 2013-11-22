package org.apache.camel.component.dropbox.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.component.dropbox.DropboxEndpoint;
import org.apache.camel.component.dropbox.api.DropboxAPIFacade;
import org.apache.camel.component.dropbox.dto.DropboxCamelResult;

import static org.apache.camel.component.dropbox.util.DropboxResultOpCode.OK;

/**
 * Created with IntelliJ IDEA.
 * User: hifly
 * Date: 11/21/13
 * Time: 4:55 PM
 * To change this template use File | Settings | File Templates.
 */
public class DropboxScheduledPollGetConsumer extends DropboxScheduledPollConsumer {

    public DropboxScheduledPollGetConsumer(DropboxEndpoint endpoint, Processor processor, DropboxConfiguration configuration) {
        super(endpoint, processor,configuration);
    }

    @Override
    protected int poll() throws Exception {
        Exchange exchange = endpoint.createExchange();
        DropboxCamelResult result = DropboxAPIFacade.getInstance(this.configuration.getClient())
                .get(this.configuration.getRemotePath());
        result.createResultOpCode(exchange,OK);
        result.populateExchange(exchange);
        LOG.info("consumer --> downloaded: " + result.toString());

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        }
        finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
