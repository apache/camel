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
package org.apache.camel.component.github.services;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryTag;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockRepositoryService extends RepositoryService {
    protected static final Logger LOG = LoggerFactory.getLogger(MockRepositoryService.class);

    private List<RepositoryTag> tags = new ArrayList<>();

    public RepositoryTag addTag(String tagName) {
        RepositoryTag tag = new RepositoryTag();
        tag.setName(tagName);
        tags.add(tag);

        return tag;
    }

    @Override
    public Repository getRepository(final String owner, final String name) {
        Repository repository = new Repository();
        User user = new User();
        user.setName(owner);
        user.setLogin(owner);
        repository.setOwner(user);
        repository.setName(name);
        return repository;
    }

    @Override
    public List<RepositoryTag> getTags(IRepositoryIdProvider repository) {
        LOG.debug("in MockRepositoryService returning {} tags", tags.size());
        return tags;
    }
}
