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
package org.apache.camel.component.wordpress.api.service.impl.ignored;

import java.util.List;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.Tag;
import org.apache.camel.component.wordpress.api.model.TagSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServiceTags;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertThat;

@Ignore("Not implemented yet")
public class WordpressServiceTagsAdapterIT {

    private static WordpressServiceTags serviceTags;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        serviceTags = serviceProvider.getService(WordpressServiceTags.class);
    }

    @Test
    public void testRetrieve() {
        final Tag tag = serviceTags.retrieve(6, null);
        assertThat(tag, not(nullValue()));
        assertThat(tag.getId(), is(6));
        assertThat(tag.getName(), not(isEmptyOrNullString()));
    }

    @Test
    public void testList() {
        final TagSearchCriteria criteria = new TagSearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(2);
        final List<Tag> revisions = serviceTags.list(criteria);
        assertThat(revisions, is(not(emptyCollectionOf(Tag.class))));
        assertThat(revisions.size(), is(2));
    }
}
