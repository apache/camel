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

import java.net.URI;
import java.net.URISyntaxException;

import net.corda.core.contracts.ContractState;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CordaConfiguration implements Cloneable {

    private transient String host;
    private transient int port;

    @UriPath @Metadata(required = true, description = "The url for the corda node")
    private String node;
    @UriParam(label = "producer", description = "Operation to use")
    private String operation;
    @UriParam(label = "security", secret = true, description = "Username for login")
    private String username;
    @UriParam(label = "security", secret = true, description = "Password for login")
    private String password;
    @UriParam(label = "consumer", defaultValue = "true", description = "Whether to process snapshots or not")
    private boolean processSnapshot = true;

    @UriParam(label = "consumer,advanced",
            description = "Start the given flow with the given arguments, returning an Observable with a single observation of the"
                    + " result of running the flow. The flowLogicClass must be annotated with net.corda.core.flows.StartableByRPC.")
    private Class<FlowLogic<?>> flowLogicClass;
    @UriParam(label = "consumer,advanced",
            description = "Start the given flow with the given arguments, returning an Observable with a single observation of the"
                    + " result of running the flow. The flowLogicClass must be annotated with net.corda.core.flows.StartableByRPC.")
    private Object[] flowLogicArguments;
    @UriParam(label = "consumer,advanced",
            description = "A contract state (or just state) contains opaque data used by a contract program. It can be thought of as a disk"
            + " file that the program can use to persist data across transactions. States are immutable: once created they are never"
            + " updated, instead, any changes must generate a new successor state. States can be updated (consumed) only once: the"
            + " notary is responsible for ensuring there is no \"double spending\" by only signing a transaction if the input states are all free.")
    private Class<ContractState> contractStateClass;
    @UriParam(label = "consumer,advanced", description = "QueryCriteria assumes underlying schema tables are correctly indexed for performance.")
    private QueryCriteria queryCriteria;
    @UriParam(label = "consumer", defaultValue = "200",
            description = "PageSpecification allows specification of a page number (starting from 1) and page size"
                    + " (defaulting to 200 with a maximum page size of (Integer.MAX_INT)"
                    + " Note: we default the page number to 200 to enable queries without requiring a page specification"
                    + " but enabling detection of large results sets that fall out of the 200 requirement."
                    + " Max page size should be used with extreme caution as results may exceed your JVM memory footprint.")
    private PageSpecification pageSpecification;
    @UriParam(label = "consumer", enums = "ASC,DESC",
            description = "Sort allows specification of a set of entity attribute names and their associated directionality"
                    + " and null handling, to be applied upon processing a query specification.")
    private Sort sort;

    public void configure() {
        try {
            URI nodeURI = new URI(node);
            this.host = nodeURI.getHost();
            this.port = nodeURI.getPort();

            if (nodeURI.getUserInfo() != null) {
                String[] creds = nodeURI.getUserInfo().split(":");
                if (getUsername() == null) {
                    setUsername(creds[0]);
                }
                if (getPassword() == null) {
                    setPassword(creds.length > 1 ? creds[1] : "");
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + node, e);
        }
    }

    public String retrieveHost() {
        return host;
    }

    public int retrievePort() {
        return port;
    }



    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

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

    public boolean isProcessSnapshot() {
        return processSnapshot;
    }

    public void setProcessSnapshot(boolean processSnapshot) {
        this.processSnapshot = processSnapshot;
    }

    public Class<FlowLogic<?>> getFlowLogicClass() {
        return flowLogicClass;
    }

    public void setFlowLogicClass(Class<FlowLogic<?>> flowLogicClass) {
        this.flowLogicClass = flowLogicClass;
    }

    public Object[] getFlowLogicArguments() {
        return flowLogicArguments;
    }

    public void setFlowLogicArguments(Object[] flowLogicArguments) {
        this.flowLogicArguments = flowLogicArguments;
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
