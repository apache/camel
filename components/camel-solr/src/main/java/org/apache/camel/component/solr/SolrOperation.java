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

import java.util.Map;
import java.util.Set;

import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.solr.converter.SolrRequestConverter;
import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.SolrPing;
import org.apache.solr.client.solrj.request.UpdateRequest;

/**
 *
 * The Solr server operations list that is implemented
 *
 * <p>
 * <ul>
 * <li>DELETE - Deletes a document from the solr collection by the record id(s)</li>
 * <li>DELETE_BY_QUERY - Deletes documents from the solr collection that match the query(queries)</li>
 * <li>INSERT - Updates the solr collection with a file, a solr-document, a bean, a list of solr-documents or a list of
 * beans</li>
 * <li>PING - Pings the solr base url</li>
 * <li>SEARCH - Searches for solr documents</li>
 * </ul>
 *
 */
public enum SolrOperation {

    DELETE(Map.of(
            SolrConstants.OPERATION_DELETE_BY_ID, "",
            SolrConstants.OPERATION_DELETE_BY_QUERY, SolrConstants.PARAM_DELETE_BY_QUERY)) {
        @Override
        SolrRequest<?> getSolrRequest(SolrProducer.ActionContext ctx) throws InvalidPayloadException {
            return SolrOperation.getSolrRequestForUpdates(ctx);
        }
    },
    INSERT(Map.of(
            SolrConstants.OPERATION_INSERT_STREAMING, "",
            SolrConstants.OPERATION_ADD_BEAN, "",
            SolrConstants.OPERATION_ADD_BEANS, "",
            SolrConstants.OPERATION_COMMIT,
            SolrConstants.HEADER_PARAM_PREFIX + SolrConstants.HEADER_PARAM_OPERATION_COMMIT,
            SolrConstants.OPERATION_SOFT_COMMIT,
            SolrConstants.HEADER_PARAM_PREFIX + SolrConstants.HEADER_PARAM_OPERATION_SOFT_COMMIT,
            SolrConstants.OPERATION_OPTIMIZE,
            SolrConstants.HEADER_PARAM_PREFIX + SolrConstants.HEADER_PARAM_OPERATION_OPTIMIZE,
            SolrConstants.OPERATION_ROLLBACK,
            SolrConstants.HEADER_PARAM_PREFIX + SolrConstants.HEADER_PARAM_OPERATION_ROLLBACK)) {
        @Override
        SolrRequest<?> getSolrRequest(SolrProducer.ActionContext ctx) throws InvalidPayloadException {
            return SolrOperation.getSolrRequestForUpdates(ctx);
        }
    },
    PING(Map.of()) {
        @Override
        SolrRequest<?> getSolrRequest(SolrProducer.ActionContext ctx) throws InvalidPayloadException {
            return ctx.exchange().getMessage().getMandatoryBody(SolrPing.class);
        }
    },
    SEARCH(Map.of(SolrConstants.OPERATION_QUERY, "")) {
        @Override
        SolrRequest<?> getSolrRequest(SolrProducer.ActionContext ctx) throws InvalidPayloadException {
            return ctx.exchange().getMessage().getMandatoryBody(QueryRequest.class);
        }
    };

    final Map<String, String> actionsToDeprecate;

    SolrOperation(Map<String, String> actionsToDeprecate) {
        this.actionsToDeprecate = actionsToDeprecate;
    }

    abstract SolrRequest<?> getSolrRequest(SolrProducer.ActionContext ctx) throws InvalidPayloadException;

    private static SolrRequest<?> getSolrRequestForUpdates(SolrProducer.ActionContext ctx) throws InvalidPayloadException {
        if (SolrRequestConverter.isUseContentStreamUpdateRequest(ctx)) {
            return ctx.exchange().getMessage().getMandatoryBody(ContentStreamUpdateRequest.class);
        }
        return ctx.exchange().getMessage().getMandatoryBody(UpdateRequest.class);
    }

    public static SolrOperation getSolrOperationFrom(String actionString) {
        try {
            return SolrOperation.valueOf(actionString);
        } catch (IllegalArgumentException ignored) {
        }
        for (SolrOperation op : SolrOperation.values()) {
            for (String futureDeprecatedAction : op.getActionsToDeprecate()) {
                if (futureDeprecatedAction.equalsIgnoreCase(actionString)) {
                    return op;
                }
            }
        }
        return null;
    }

    public Set<String> getActionsToDeprecate() {
        return actionsToDeprecate.keySet();
    }

    public String getActionParameter(String actionString) {
        String actionParameter = actionsToDeprecate.get(actionString);
        return ObjectHelper.isNotEmpty(actionParameter)
                ? actionParameter
                : null;
    }

    public String createFutureDeprecationMessage(String actionString, String actionParameter) {
        String message = String.format(
                "The operation obtained from the exchange header '%s=%s' is going to be deprecated in future versions of camel-solr."
                                       + " Please use the operation value '%1$s=%s' instead",
                SolrConstants.PARAM_OPERATION,
                actionString,
                name());
        if (actionParameter == null) {
            message += ".";
        } else {
            message += "and add the header '" + actionParameter
                       + "=true' to the exchange for the desired operation. ";
            if (!actionString.equals(SolrConstants.OPERATION_DELETE_BY_QUERY)) {
                message += "For info on the solr parameters for commit related update requests, have a look at the solr documentation "
                           + "on https://solr.apache.org/guide/solr/latest/configuration-guide/commits-transaction-logs.html#explicit-commits";
            }
        }
        return message;
    }

}
