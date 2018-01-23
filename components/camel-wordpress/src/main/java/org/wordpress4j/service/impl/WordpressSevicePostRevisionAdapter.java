package org.wordpress4j.service.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.PostRevision;
import org.wordpress4j.service.WordpressServicePostRevision;
import org.wordpress4j.service.spi.PostRevisionsSPI;

public class WordpressSevicePostRevisionAdapter extends AbstractWordpressServiceAdapter<PostRevisionsSPI> implements WordpressServicePostRevision {

    public WordpressSevicePostRevisionAdapter(final String wordpressUrl, final String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<PostRevisionsSPI> getSpiType() {
        return PostRevisionsSPI.class;
    }

    @Override
    public void delete(Integer postId, Integer revisionId) {
        checkArgument(postId > 0, "Please define a post id");
        checkArgument(revisionId > 0, "Please define a revision id");
        this.getSpi().delete(this.getApiVersion(), postId, revisionId);
    }

    @Override
    public PostRevision retrieve(Integer postId, Integer revisionId, Context context) {
        checkArgument(postId > 0, "Please define a post id");
        checkArgument(revisionId > 0, "Please define a revision id");
        return this.getSpi().retrieveRevision(this.getApiVersion(), postId, revisionId, context);
    }

    @Override
    public List<PostRevision> list(Integer postId, Context context) {
        checkArgument(postId > 0, "Please define a post id");
        return this.getSpi().list(this.getApiVersion(), postId, context);
    }

}
