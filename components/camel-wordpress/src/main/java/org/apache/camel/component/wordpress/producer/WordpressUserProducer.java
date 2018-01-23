package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.User;
import org.wordpress4j.service.WordpressServiceUsers;

public class WordpressUserProducer extends AbstractWordpressProducer<User> {
    
    private WordpressServiceUsers serviceUsers;

    public WordpressUserProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    protected User processDelete(Exchange exchange) {
        LOG.debug("Trying to delete user {}", getConfiguration().getId());
        return serviceUsers.delete(getConfiguration().getId());
    }
    
    protected User processUpdate(Exchange exchange) {
        LOG.debug("Trying to update the post {} with id {}", exchange.getIn().getBody(),
                  this.getConfiguration().getId());
        return serviceUsers.update(getConfiguration().getId(), exchange.getIn().getBody(User.class));
    }
    
    protected User processInsert(Exchange exchange) {
        LOG.debug("Trying to create a new user{}", exchange.getIn().getBody());
        return serviceUsers.create(exchange.getIn().getBody(User.class));
    }

}
