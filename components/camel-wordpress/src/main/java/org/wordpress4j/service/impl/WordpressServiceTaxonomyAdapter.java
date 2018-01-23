package org.wordpress4j.service.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

import java.util.Map;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.Taxonomy;
import org.wordpress4j.service.WordpressServiceTaxonomy;
import org.wordpress4j.service.spi.TaxonomySPI;

public class WordpressServiceTaxonomyAdapter extends AbstractWordpressServiceAdapter<TaxonomySPI> implements WordpressServiceTaxonomy {

    public WordpressServiceTaxonomyAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<TaxonomySPI> getSpiType() {
        return TaxonomySPI.class;
    }
    
    @Override
    public Map<String, Taxonomy> list(Context context, String postType) {
        return getSpi().list(this.getApiVersion(), context, postType);
    }
    
    @Override
    public Taxonomy retrieve(Context context, String taxonomy) {
        checkNotNull(emptyToNull(taxonomy), "Please define a taxonomy");
        return getSpi().retrieve(this.getApiVersion(), context, taxonomy);
    }

}
