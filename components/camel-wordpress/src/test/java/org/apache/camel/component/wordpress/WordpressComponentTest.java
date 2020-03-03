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
package org.apache.camel.component.wordpress;

import org.apache.camel.component.wordpress.api.model.PostOrderBy;
import org.apache.camel.component.wordpress.api.model.PostSearchCriteria;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.not;

public class WordpressComponentTest extends CamelTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressComponentTest.class);

    @Test
    public void testParseUriPropertiesCriteria() throws Exception {
        final WordpressComponent component = new WordpressComponent(context);
        component.init();

        final WordpressEndpoint endpoint = (WordpressEndpoint)component
            .createEndpoint("wordpress:post?apiVersion=2&url=http://mysite.com/&criteria.search=test&criteria.page=1&criteria.perPage=10&criteria.orderBy=author&criteria.categories=camel,dozer,json");

        assertThat(endpoint.getConfiguration().getSearchCriteria(), instanceOf(PostSearchCriteria.class));
        assertNotNull(endpoint.getConfiguration().getSearchCriteria());
        assertThat(endpoint.getConfiguration().getSearchCriteria().getPage(), is(1));
        assertThat(endpoint.getConfiguration().getSearchCriteria().getPerPage(), is(10));
        assertThat(endpoint.getConfiguration().getSearchCriteria().getSearch(), is("test"));
        assertThat(((PostSearchCriteria)endpoint.getConfiguration().getSearchCriteria()).getOrderBy(), is(PostOrderBy.author));
        assertThat(((PostSearchCriteria)endpoint.getConfiguration().getSearchCriteria()).getCategories(), notNullValue());
        assertThat(((PostSearchCriteria)endpoint.getConfiguration().getSearchCriteria()).getCategories(), not(emptyCollectionOf(String.class)));

        LOGGER.info("Categories are {}", ((PostSearchCriteria)endpoint.getConfiguration().getSearchCriteria()).getCategories());
    }

}
