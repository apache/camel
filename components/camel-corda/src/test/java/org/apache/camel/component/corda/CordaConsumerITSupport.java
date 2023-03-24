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

import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_NUM;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.MAX_PAGE_SIZE;

public class CordaConsumerITSupport extends CordaITSupport {

    @Override
    public boolean isUseAdviceWith() {
        return false;
    }

    @BindToRegistry("arguments")
    public String[] addArgs() {
        return new String[] { "Hello" };
    }

    @BindToRegistry("flowLociClass")
    public Class<FlowLogic<String>> addFlowLociClass() throws Exception {
        return (Class<FlowLogic<String>>) Class.forName("org.apache.camel.component.corda.CamelFlow");
    }

    @BindToRegistry("queryCriteria")
    public QueryCriteria addCriteria() {
        return new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.CONSUMED);
    }

    @BindToRegistry("pageSpecification")
    public PageSpecification addPageSpec() {
        return new PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE);
    }

    @BindToRegistry("contractStateClass")
    public Class<OwnableState> addContractStateClass() {
        return OwnableState.class;
    }

    @BindToRegistry("sort")
    public Sort.SortColumn addSort() {
        return new Sort.SortColumn(new SortAttribute.Standard(Sort.LinearStateAttribute.UUID), Sort.Direction.DESC);
    }
}
