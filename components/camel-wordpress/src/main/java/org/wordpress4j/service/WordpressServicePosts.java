package org.wordpress4j.service;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.Post;
import org.wordpress4j.model.PostSearchCriteria;

public interface WordpressServicePosts extends WordpressCrudService<Post, PostSearchCriteria> {
    
    /**
     * Default endpoint.
     * 
     * @param postId
     * @param context
     * @param password
     * @return
     */
    Post retrieve(Integer postId, Context context, String password);

}
