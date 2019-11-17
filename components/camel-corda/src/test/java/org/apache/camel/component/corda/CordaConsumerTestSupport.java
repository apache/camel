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
package org.apache.camel.component.corda;

import net.corda.core.contracts.OwnableState;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import net.corda.core.node.services.vault.SortAttribute;
import org.apache.camel.BindToRegistry;
import org.junit.Ignore;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_NUM;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.MAX_PAGE_SIZE;

@Ignore("This integration test requires a locally running corda node such cordapp-template-java")
public class CordaConsumerTestSupport extends CordaTestSupport {

    @Override
    public boolean isUseAdviceWith() {
        return false;
    }
    
    @BindToRegistry("arguments")
    public String[] addArgs() {
        String [] args = new String[] {"Hello"};
        return args;
    }

    @BindToRegistry("flowLociClass")
    public Class<FlowLogic<String>> addFlowLociClass() throws Exception {
        Class<FlowLogic<String>> flowLociClass = (Class<FlowLogic<String>>) Class.forName("org.apache.camel.component.corda.CamelFlow");
        return flowLociClass;
    }
    
    @BindToRegistry("queryCriteria")
    public QueryCriteria addCriteria() {
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
        return criteria;
    }
    
    @BindToRegistry("pageSpecification")
    public PageSpecification addPageSpec() {
        PageSpecification pageSpec = new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
        return pageSpec;
    }
    
    @BindToRegistry("contractStateClass")
    public Class<OwnableState> addContractStateClass() {
        return OwnableState.class;
    }
    
    @BindToRegistry("sort")
    public Sort.SortColumn addSort() {
        Sort.SortColumn sortByUid = new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
        return sortByUid;
    }
}
