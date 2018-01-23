package org.apache.camel.component.wordpress.consumer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.Post;
import org.wordpress4j.model.PostSearchCriteria;
import org.wordpress4j.service.WordpressServicePosts;

/**
 * Consumer for Posts. Adapter for {@link WordpressServicePosts} read only
 * methods (list and retrieve).
 */
public class WordpressPostConsumer extends AbstractWordpressConsumer {

    private WordpressServicePosts servicePosts;

    public WordpressPostConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    public WordpressPostConsumer(WordpressEndpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    @Override
    protected int poll() throws Exception {
        if (this.getConfiguration().getId() == null) {
            return this.pollForPostList();
        } else {
            return this.pollForSingle();
        }
    }

    private int pollForPostList() {
        final List<Post> posts = this.servicePosts.list((PostSearchCriteria)getConfiguration().getSearchCriteria());
        posts.stream().forEach(p -> this.process(p));
        return posts.size();
    }

    private int pollForSingle() {
        final Post post = this.servicePosts.retrieve(getConfiguration().getId());
        if (post == null) {
            return 0;
        }
        this.process(post);
        return 1;
    }
}
