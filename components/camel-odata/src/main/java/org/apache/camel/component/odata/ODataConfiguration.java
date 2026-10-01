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
package org.apache.camel.component.odata;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class ODataConfiguration {

    @UriParam(label = "producer", description = "The OData operation to perform")
    private ODataOperation operation = ODataOperation.READ_SET;

    @UriParam(label = "producer", description = "The OData $filter query parameter")
    private String filter;

    @UriParam(label = "producer", description = "The OData $select query parameter")
    private String select;

    @UriParam(label = "producer", description = "The OData $expand query parameter")
    private String expand;

    @UriParam(label = "producer", description = "The OData $orderby query parameter")
    private String orderBy;

    @UriParam(label = "producer", description = "The OData $top query parameter")
    private Integer top;

    @UriParam(label = "producer", description = "The OData $skip query parameter")
    private Integer skip;

    @UriParam(label = "producer", description = "The OData $count query parameter")
    private Boolean count;

    @UriParam(label = "security", description = "Authentication method to use (e.g., Basic, Bearer)")
    private String authMethod;

    @UriParam(label = "security", description = "Username for Basic authentication")
    private String authUsername;

    @UriParam(label = "security", description = "Password for Basic authentication")
    private String authPassword;

    @UriParam(label = "security", description = "Bearer token for Bearer authentication")
    private String authBearerToken;

    @UriParam(label = "security", description = "To use a custom SSLContextParameters")
    private SSLContextParameters sslContextParameters;

    public ODataOperation getOperation() {
        return operation;
    }

    public void setOperation(ODataOperation operation) {
        this.operation = operation;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getSelect() {
        return select;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    public String getExpand() {
        return expand;
    }

    public void setExpand(String expand) {
        this.expand = expand;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public Integer getTop() {
        return top;
    }

    public void setTop(Integer top) {
        this.top = top;
    }

    public Integer getSkip() {
        return skip;
    }

    public void setSkip(Integer skip) {
        this.skip = skip;
    }

    public Boolean getCount() {
        return count;
    }

    public void setCount(Boolean count) {
        this.count = count;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public String getAuthBearerToken() {
        return authBearerToken;
    }

    public void setAuthBearerToken(String authBearerToken) {
        this.authBearerToken = authBearerToken;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }
}
