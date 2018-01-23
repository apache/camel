package org.wordpress4j.service;

import java.util.Map;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.Taxonomy;

public interface WordpressServiceTaxonomy extends WordpressService {

    Map<String, Taxonomy> list(Context context, String postType);
    
    Taxonomy retrieve(Context context, String taxonomy);

}
