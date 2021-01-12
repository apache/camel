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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.test.junit5.params.Test;
import org.apache.solr.client.solrj.beans.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SolrAddBeansTest extends SolrComponentTestSupport {

    @Test
    public void testAddBeans() throws Exception {

        List<Item> beans = new ArrayList<>();

        //add bean1
        Item item1 = new Item();
        item1.id = TEST_ID;
        item1.categories = new String[] { "aaa", "bbb", "ccc" };
        beans.add(item1);

        //add bean2
        Item item2 = new Item();
        item2.id = TEST_ID2;
        item2.categories = new String[] { "aaa", "bbb", "ccc" };
        beans.add(item2);

        template.sendBodyAndHeader("direct:start", beans, SolrConstants.OPERATION, SolrConstants.OPERATION_ADD_BEANS);
        template.sendBodyAndHeader("direct:start", null, SolrConstants.OPERATION, SolrConstants.OPERATION_COMMIT);

        //verify
        assertEquals(1, executeSolrQuery("id:" + TEST_ID).getResults().getNumFound(), "wrong number of entries found");
        assertEquals(1, executeSolrQuery("id:" + TEST_ID2).getResults().getNumFound(), "wrong number of entries found");
        assertEquals(2, executeSolrQuery("*:*").getResults().getNumFound(), "wrong number of entries found");
    }

    public class Item {
        @Field
        String id;

        @Field("cat")
        String[] categories;
    }
}
