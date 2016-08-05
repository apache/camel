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
package org.apache.camel.component.solr;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("Need refactoring in SolrComponentTestSupport, with new schema and solr-config from solr 5.2.1 and new Cloud Solr cluster instantiation")
public class SolrTransactionsTest extends SolrComponentTestSupport {

    public SolrTransactionsTest(SolrFixtures.TestServerType serverToTest) {
        super(serverToTest);
    }

    @Test
    public void testCommit() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals("wrong number of entries found", 0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());

        //commit
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);

        //verify exists after commit
        assertEquals("wrong number of entries found", 1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());
    }

    @Test
    public void testRollback() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals("wrong number of entries found", 0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());

        //rollback
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_ROLLBACK);

        //verify after rollback
        assertEquals("wrong number of entries found", 0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());

        //commit
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);

        //verify after commit (again)
        assertEquals("wrong number of entries found", 0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());
    }

    @Test
    public void testOptimize() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals("wrong number of entries found", 0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());

        //optimize
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_OPTIMIZE);

        //verify exists after optimize
        assertEquals("wrong number of entries found", 1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound());
    }

}
