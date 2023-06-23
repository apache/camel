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

import java.util.Objects;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.DeletedModel;
import org.apache.camel.component.wordpress.api.model.SearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressCrudService;

/**
 * Base service adapter implementation with CRUD commons operations.
 *
 * @param <A>
 * @param <T>
 */
abstract class AbstractWordpressCrudServiceAdapter<A, T, S extends SearchCriteria> extends AbstractWordpressServiceAdapter<A>
        implements WordpressCrudService<T, S> {

    AbstractWordpressCrudServiceAdapter(final String wordpressUrl, final String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    public final T create(T object) {
        Objects.requireNonNull(object, "Please define an object to create");
        return this.doCreate(object);
    }

    protected abstract T doCreate(T object);

    @Override
    public final T delete(Integer id) {
        if (!(id > 0)) {
            throw new IllegalArgumentException("The id is mandatory");
        }
        return this.doDelete(id);
    }

    @Override
    public final DeletedModel<T> forceDelete(Integer id) {
        if (!(id > 0)) {
            throw new IllegalArgumentException("The id is mandatory");
        }
        return this.doForceDelete(id);
    }

    protected abstract T doDelete(Integer id);

    protected DeletedModel<T> doForceDelete(Integer id) {
        final DeletedModel<T> deletedModel = new DeletedModel<>();

        deletedModel.setPrevious(this.doDelete(id));
        deletedModel.setDeleted(false);

        return deletedModel;
    }

    @Override
    public final T update(Integer id, T object) {
        Objects.requireNonNull(object, "Please define an object to update");
        if (!(id > 0)) {
            throw new IllegalArgumentException("The id is mandatory");
        }
        return this.doUpdate(id, object);
    }

    protected abstract T doUpdate(Integer id, T object);

    @Override
    public T retrieve(Integer entityID) {
        return this.retrieve(entityID, Context.view);
    }

    @Override
    public final T retrieve(Integer entityID, Context context) {
        if (!(entityID > 0)) {
            throw new IllegalArgumentException("Please provide a non zero id");
        }
        Objects.requireNonNull(context, "Provide a context");
        // return this.getSpi().retrieve(getApiVersion(), entityID, context);
        return doRetrieve(entityID, context);
    }

    protected abstract T doRetrieve(Integer entityID, Context context);

}
