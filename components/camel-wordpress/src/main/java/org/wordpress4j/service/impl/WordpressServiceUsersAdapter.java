package org.wordpress4j.service.impl;

import java.util.List;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.DeletedModel;
import org.wordpress4j.model.User;
import org.wordpress4j.model.UserSearchCriteria;
import org.wordpress4j.service.WordpressServiceUsers;
import org.wordpress4j.service.spi.UsersSPI;

public class WordpressServiceUsersAdapter extends AbstractWordpressCrudServiceAdapter<UsersSPI, User, UserSearchCriteria> implements WordpressServiceUsers {

    public WordpressServiceUsersAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    public List<User> list(UserSearchCriteria s) {
        // @formatter:off
        return getSpi().list(getApiVersion(), 
                             s.getContext(), 
                             s.getPage(), 
                             s.getPerPage(), 
                             s.getSearch(),
                             s.getExclude(), 
                             s.getInclude(), 
                             s.getOffset(), 
                             s.getOrder(),
                             s.getOrderBy(), 
                             s.getSlug(), 
                             s.getRoles());
        // @formatter:on
    }

    @Override
    protected Class<UsersSPI> getSpiType() {
        return UsersSPI.class;
    }

    @Override
    protected User doCreate(User object) {
        return getSpi().create(getApiVersion(), object);
    }

    @Override
    protected DeletedModel<User> doForceDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id, true, 1);
    }

    @Override
    protected User doDelete(Integer id) {
        return this.forceDelete(id).getPrevious();
    }

    @Override
    protected User doUpdate(Integer id, User object) {
        return getSpi().update(getApiVersion(), id, object);
    }

    @Override
    protected User doRetrieve(Integer entityID, Context context) {
        return getSpi().retrieve(getApiVersion(), entityID, context);
    }
}
