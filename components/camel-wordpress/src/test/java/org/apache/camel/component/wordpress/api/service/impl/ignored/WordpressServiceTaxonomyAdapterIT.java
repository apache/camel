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

import java.util.Map;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.Taxonomy;
import org.apache.camel.component.wordpress.api.service.WordpressServiceTaxonomy;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertThat;

@Ignore("Not implemented yet")
public class WordpressServiceTaxonomyAdapterIT {

    private static WordpressServiceTaxonomy serviceTaxonomy;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        serviceTaxonomy = serviceProvider.getService(WordpressServiceTaxonomy.class);
    }

    @Test
    public void testRetrieve() {
        final Taxonomy taxonomy = serviceTaxonomy.retrieve(null, "category");
        assertThat(taxonomy, not(nullValue()));
        assertThat(taxonomy.getName(), not(isEmptyOrNullString()));
    }

    @Test
    public void testList() {
        final Map<String, Taxonomy> taxs = serviceTaxonomy.list(null, null);
        assertThat(taxs, is(not(nullValue())));
        assertThat(taxs.size(), is(2));
    }
}
