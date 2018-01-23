package org.wordpress4j.service.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.wordpress4j.model.Category;
import org.wordpress4j.model.CategorySearchCriteria;
import org.wordpress4j.model.Context;
import org.wordpress4j.service.WordpressServiceCategories;
import org.wordpress4j.service.spi.CategoriesSPI;

public class WordpressServiceCategoriesAdapter extends AbstractWordpressCrudServiceAdapter<CategoriesSPI, Category, CategorySearchCriteria> implements WordpressServiceCategories  {

    public WordpressServiceCategoriesAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<CategoriesSPI> getSpiType() {
        return CategoriesSPI.class;
    }

    //@formatter:off
    @Override
    public List<Category> list(CategorySearchCriteria criteria) {
        checkNotNull(criteria, "The search criteria must be defined");
        return getSpi().list(this.getApiVersion(), 
                             criteria.getContext(), 
                             criteria.getPage(), 
                             criteria.getPerPage(), 
                             criteria.getSearch(), 
                             criteria.getExclude(), 
                             criteria.getInclude(),
                             criteria.getOrder(), 
                             criteria.getOrderBy(), 
                             criteria.isHideEmpty(), 
                             criteria.getParent(), 
                             criteria.getPostId(), 
                             criteria.getSlug());
    }
    //@formatter:on

    @Override
    protected Category doCreate(Category object) {
        return getSpi().create(getApiVersion(), object);
    }

    @Override
    protected Category doDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id, false);
    }

    @Override
    protected Category doUpdate(Integer id, Category object) {
        return getSpi().update(getApiVersion(), id, object);
    }

    @Override
    protected Category doRetrieve(Integer entityID, Context context) {
        return getSpi().retrieve(getApiVersion(), entityID, context);
    }

}
