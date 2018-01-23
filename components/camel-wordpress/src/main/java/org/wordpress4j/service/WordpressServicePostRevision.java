package org.wordpress4j.service;

import java.util.List;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.PostRevision;

public interface WordpressServicePostRevision extends WordpressService {
    
    void delete(Integer postId, Integer revisionId);
    
    PostRevision retrieve(Integer postId, Integer revisionId, Context context);
    
    List<PostRevision> list(Integer postId, Context context);

}
