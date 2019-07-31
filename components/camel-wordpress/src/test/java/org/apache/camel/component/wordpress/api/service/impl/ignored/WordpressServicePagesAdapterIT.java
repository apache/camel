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
import org.apache.camel.component.wordpress.api.model.Page;
import org.apache.camel.component.wordpress.api.model.PageSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServicePages;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.junit.Assert.assertThat;

@Ignore("Not implemented yet")
public class WordpressServicePagesAdapterIT {

    private static WordpressServicePages servicePages;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        servicePages = serviceProvider.getService(WordpressServicePages.class);
    }

    @Test
    public void testRetrieve() {
        final Page page = servicePages.retrieve(2, null, null);
        assertThat(page, not(nullValue()));
        assertThat(page.getId(), is(2));
    }

    @Test
    public void testList() {
        final PageSearchCriteria criteria = new PageSearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(5);
        final List<Page> posts = servicePages.list(criteria);
        assertThat(posts, is(not(emptyCollectionOf(Page.class))));
        assertThat(posts.size(), is(5));
    }
}
