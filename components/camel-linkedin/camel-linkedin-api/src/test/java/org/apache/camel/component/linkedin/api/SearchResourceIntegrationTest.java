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

import org.apache.camel.component.linkedin.api.model.CompanySearch;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration test for {@link org.apache.camel.component.linkedin.api.SearchResource}
 */
public class SearchResourceIntegrationTest extends AbstractResourceIntegrationTest {

    private static SearchResource searchResource;

    @BeforeClass
    public static void beforeClass() throws Exception {
        AbstractResourceIntegrationTest.beforeClass();
        searchResource = getResource(SearchResource.class);
    }

    @Test
    public void testSearchCompanies() {
        execute(new Runnable() {
            @Override
            public void run() {
                final CompanySearch companySearch = searchResource.searchCompanies(DEFAULT_FIELDS, "linkedin", null, null,
                    null, null, null, null);
                assertNotNull(companySearch);
            }
        });
    }
}
