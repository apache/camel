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
package org.apache.camel.component.corda;

import net.corda.core.contracts.ContractState;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class CordaConfiguration implements Cloneable {
    @UriParam
    @Metadata(required = true)
    private String operation;

    @UriParam
    @Metadata(required = true, secret = true)
    private String username;

    @UriParam
    @Metadata(required = true, secret = true)
    private String password;

    @Metadata(required = true)
    private String host;

    @Metadata(required = true)
    private int port;

    @Metadata(required = false, defaultValue = "true")
    private boolean processSnapshot = true;

    private Class<FlowLogic<?>> flowLociClass;

    private Object [] arguments;

    private Class<ContractState> contractStateClass;

    private QueryCriteria queryCriteria;

    private PageSpecification pageSpecification;

    private Sort sort;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isProcessSnapshot() {
        return processSnapshot;
    }

    public void setProcessSnapshot(boolean processSnapshot) {
        this.processSnapshot = processSnapshot;
    }

    public Class<FlowLogic<?>> getFlowLociClass() {
        return flowLociClass;
    }

    public void setFlowLociClass(Class<FlowLogic<?>> flowLociClass) {
        this.flowLociClass = flowLociClass;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public Class<ContractState> getContractStateClass() {
        return contractStateClass;
    }

    public void setContractStateClass(Class<ContractState> contractStateClass) {
        this.contractStateClass = contractStateClass;
    }

    public QueryCriteria getQueryCriteria() {
        return queryCriteria;
    }

    public void setQueryCriteria(QueryCriteria queryCriteria) {
        this.queryCriteria = queryCriteria;
    }

    public PageSpecification getPageSpecification() {
        return pageSpecification;
    }

    public void setPageSpecification(PageSpecification pageSpecification) {
        this.pageSpecification = pageSpecification;
    }

    public Sort getSort() {
        return sort;
    }

    public void setSort(Sort sort) {
        this.sort = sort;
    }

    public CordaConfiguration copy() {
        try {
            return (CordaConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
