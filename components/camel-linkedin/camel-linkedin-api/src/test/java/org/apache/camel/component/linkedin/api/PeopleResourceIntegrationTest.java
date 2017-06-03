/**
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
package org.apache.camel.component.linkedin.api;

import java.util.Date;

import org.apache.camel.component.linkedin.api.model.GroupMemberships;
import org.apache.camel.component.linkedin.api.model.JobSuggestions;
import org.apache.camel.component.linkedin.api.model.MembershipStateCode;
import org.apache.camel.component.linkedin.api.model.Order;
import org.apache.camel.component.linkedin.api.model.Person;
import org.apache.camel.component.linkedin.api.model.PostCategoryCode;
import org.apache.camel.component.linkedin.api.model.PostRole;
import org.apache.camel.component.linkedin.api.model.Posts;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration test for {@link PeopleResource}.
 */
public class PeopleResourceIntegrationTest extends AbstractResourceIntegrationTest {

    private static PeopleResource peopleResource;

    @BeforeClass
    public static void beforeClass() throws Exception {
        AbstractResourceIntegrationTest.beforeClass();

        final Class<PeopleResource> resourceClass = PeopleResource.class;
        PeopleResourceIntegrationTest.peopleResource = getResource(resourceClass);
    }

    @Test
    public void testGetPerson() throws Exception {
        execute(new Runnable() {
            @Override
            public void run() {
                final Person person = peopleResource.getPerson(":(id)", true);
                assertNotNull(person);
                assertNotNull(person.getId());
                LOG.debug("getPerson result: " + person);
            }
        });
    }

    @Test
    public void testGetPosts() throws Exception {
        execute(new Runnable() {
            @Override
            public void run() {
                final GroupMemberships groupMemberships = peopleResource.getGroupMemberships(MembershipStateCode.MEMBER,
                    "", null, null);
                assertNotNull(groupMemberships);
                assertNotNull(groupMemberships.getGroupMembershipList());
                assertFalse(groupMemberships.getGroupMembershipList().isEmpty());
                final Posts posts = peopleResource.getPosts(Long.parseLong(
                        groupMemberships.getGroupMembershipList().get(0).getGroup().getId()), null, null,
                    Order.RECENCY, PostRole.FOLLOWER, PostCategoryCode.DISCUSSION, null, ":(id)");
                assertNotNull(posts);
                LOG.debug("getPosts result: " + posts);
            }
        });
    }

    @Test(expected = LinkedInException.class)
    public void testLinkedInError() throws Exception {
        execute(new Runnable() {
            @Override
            public void run() {
                peopleResource.getPerson("bad_fields_selector", true);
            }
        });
    }

    @Ignore("CXF swallows application exceptions from ClientResponseFilters")
    @Test(expected = LinkedInException.class)
    public void testLinkedInException() throws Exception {
        try {
            peopleResource.getPerson("bad_fields_selector", true);
        } catch (LinkedInException e) {
            assertNotNull(e.getError());
            LOG.debug("getPerson error: " + e.getMessage());
            throw e;
        }
    }

    @Test
    public void testOAuthTokenRefresh() throws Exception {
        peopleResource.getPerson("", false);

        // mark OAuth token as expired
        final OAuthToken oAuthToken = requestFilter.getOAuthToken();
        oAuthToken.setExpiryTime(new Date().getTime());

        peopleResource.getPerson("", false);
    }

    @Test
    public void testGetSuggestedJobs() throws Exception {
        execute(new Runnable() {
            @Override
            public void run() {
                final JobSuggestions suggestedJobs = peopleResource.getSuggestedJobs(DEFAULT_FIELDS);
                assertNotNull(suggestedJobs);
                LOG.debug("Suggested Jobs " + suggestedJobs.getJobs());
            }
        });
    }
}
