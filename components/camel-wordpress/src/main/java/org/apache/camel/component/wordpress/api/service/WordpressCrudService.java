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
package org.apache.camel.component.wordpress.api.service;

import java.util.List;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.DeletedModel;
import org.apache.camel.component.wordpress.api.model.SearchCriteria;

/**
 * Common interface for services performing CRUD operations
 * 
 * @param <T> model type
 * @param <S> {@link SearchCriteria} used for this model
 */
public interface WordpressCrudService<T, S extends SearchCriteria> extends WordpressService {

    T retrieve(Integer entityID, Context context);

    T retrieve(Integer entityID);

    T create(T entity);

    T delete(Integer entityID);

    DeletedModel<T> forceDelete(Integer entityID);

    List<T> list(S searchCriteria);

    T update(Integer entityID, T entity);
}
