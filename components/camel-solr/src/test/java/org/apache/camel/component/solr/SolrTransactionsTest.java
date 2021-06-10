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
package org.apache.camel.component.solr;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit5.params.Test;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SolrTransactionsTest extends SolrComponentTestSupport {

    @Test
    public void testCommit() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //commit
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);

        //verify exists after commit
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testSoftCommit() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //commit
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_SOFT_COMMIT);

        //verify exists after commit
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testRollback() throws Exception {

        if (SolrFixtures.TestServerType.USE_CLOUD == getSolrFixtures().serverType) {
            // Twisting expectations in this case as rollback is currently no
            // more supported in SolrCloud mode. See SOLR-4895
            Exception e = assertThrows(CamelExecutionException.class,
                    () -> doRollback());
            assertIsInstanceOf(BaseHttpSolrClient.RemoteSolrException.class, e.getCause());
            assertTrue(
                    e.getCause().getMessage().contains("Rollback is currently not supported in SolrCloud mode. (SOLR-4895)"));
        } else {
            doRollback();
        }

    }

    protected void doRollback() throws Exception {
        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //rollback
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_ROLLBACK);

        //verify after rollback
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //commit
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);

        //verify after commit (again)
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

    @Test
    public void testOptimize() throws Exception {

        //insert and verify
        solrInsertTestEntry();
        assertEquals(0, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");

        //optimize
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_OPTIMIZE);

        //verify exists after optimize
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
    }

}
