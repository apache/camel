package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.Post;
import org.wordpress4j.service.WordpressServicePosts;

/**
 * The Wordpress Post producer.
 */
public class WordpressPostProducer extends AbstractWordpressProducer<Post> {
    private WordpressServicePosts servicePosts;

    public WordpressPostProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    protected Post processInsert(Exchange exchange) {
        LOG.debug("Trying to create a new blog post with {}", exchange.getIn().getBody());
        return servicePosts.create(exchange.getIn().getBody(Post.class));
    }

    protected Post processUpdate(Exchange exchange) {
        LOG.debug("Trying to update the post {} with id {}", exchange.getIn().getBody(),
                this.getConfiguration().getId());
        return servicePosts.update(this.getConfiguration().getId(), exchange.getIn().getBody(Post.class));
    }

    protected Post processDelete(Exchange exchange) {
        LOG.debug("Trying to delete a post with id {}", this.getConfiguration().getId());
        
        if(this.getConfiguration().isForce()) {
            return servicePosts.forceDelete(this.getConfiguration().getId()).getPrevious();
        } else {
            return servicePosts.delete(this.getConfiguration().getId());
        }
    }

}
