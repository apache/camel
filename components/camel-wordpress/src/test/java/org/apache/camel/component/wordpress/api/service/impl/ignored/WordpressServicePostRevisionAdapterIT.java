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
import org.apache.camel.component.wordpress.api.WordpressAPIConfiguration;
import org.apache.camel.component.wordpress.api.WordpressConstants;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.auth.WordpressAuthentication;
import org.apache.camel.component.wordpress.api.auth.WordpressBasicAuthentication;
import org.apache.camel.component.wordpress.api.model.PostRevision;
import org.apache.camel.component.wordpress.api.service.WordpressServicePostRevision;
import org.apache.camel.component.wordpress.api.test.WordpressMockServerTestSupport;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

/*
 * TODO fix authentication problem (when implementing global authentication) 
 * javax.ws.rs.NotAuthorizedException: HTTP 401 Unauthorized
 */
@Ignore("Not implemented yet")
public class WordpressServicePostRevisionAdapterIT extends WordpressMockServerTestSupport {

    private static WordpressServicePostRevision servicePostRevision;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        final WordpressAuthentication authentication = new WordpressBasicAuthentication("integration_test", "JD)e)Ox)z@HyDF*Dv4aWszm*");
        final WordpressAPIConfiguration configuration = new WordpressAPIConfiguration(WordpressTestConstants.WORDPRESS4J_URL, WordpressConstants.API_VERSION);
        configuration.setAuthentication(authentication);
        serviceProvider.init(configuration);
        servicePostRevision = serviceProvider.getService(WordpressServicePostRevision.class);
    }

    @Test
    public void testRetrieve() {
        final PostRevision revision = servicePostRevision.retrieve(1, 1, null);
        assertThat(revision, not(nullValue()));
        assertThat(revision.getId(), is(1));
        assertThat(revision.getGuid(), notNullValue());
    }

    @Test
    public void testList() {
        final List<PostRevision> revisions = servicePostRevision.list(1, null);
        assertThat(revisions, is(not(emptyCollectionOf(PostRevision.class))));
        assertThat(revisions.size(), greaterThan(0));
    }
}
