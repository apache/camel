/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.wordpress.api.service.impl;

import java.util.List;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.DeletedModel;
import org.apache.camel.component.wordpress.api.model.User;
import org.apache.camel.component.wordpress.api.model.UserSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServiceUsers;
import org.apache.camel.component.wordpress.api.service.spi.UsersSPI;

public class WordpressServiceUsersAdapter extends AbstractWordpressCrudServiceAdapter<UsersSPI, User, UserSearchCriteria> implements WordpressServiceUsers {

    public WordpressServiceUsersAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    public List<User> list(UserSearchCriteria s) {
        // @formatter:off
        return getSpi().list(getApiVersion(), s.getContext(), s.getPage(), s.getPerPage(), s.getSearch(), s.getExclude(), s.getInclude(), s.getOffset(), s.getOrder(), s.getOrderBy(), s.getSlug(),
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
