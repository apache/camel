package org.apache.camel.component.wordpress.consumer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.User;
import org.wordpress4j.model.UserSearchCriteria;
import org.wordpress4j.service.WordpressServiceUsers;

public class WordpressUserConsumer extends AbstractWordpressConsumer {

    private WordpressServiceUsers serviceUsers;

    public WordpressUserConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    public WordpressUserConsumer(WordpressEndpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        serviceUsers = WordpressServiceProvider.getInstance().getService(WordpressServiceUsers.class);
    }

    @Override
    protected int poll() throws Exception {
        if (getConfiguration().getId() == null) {
            return this.pollForList();
        } else {
            return this.pollForSingle();
        }
    }

    private int pollForSingle() {
        final User user = this.serviceUsers.retrieve(getConfiguration().getId());
        if (user == null) {
            return 0;
        }
        this.process(user);
        return 1;
    }

    private int pollForList() {
        final List<User> users = this.serviceUsers.list((UserSearchCriteria)getConfiguration().getSearchCriteria());
        users.stream().forEach(p -> this.process(p));
        LOG.trace("returned users is {}", users);
        return users.size();
    }

}
