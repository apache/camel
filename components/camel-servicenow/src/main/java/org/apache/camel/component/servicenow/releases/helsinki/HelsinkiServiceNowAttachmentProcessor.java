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

package org.apache.camel.component.servicenow.releases.helsinki;

import java.io.InputStream;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.AbstractServiceNowProcessor;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowParams;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_CONTENT;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_DELETE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_UPLOAD;

public class HelsinkiServiceNowAttachmentProcessor extends AbstractServiceNowProcessor {
    protected HelsinkiServiceNowAttachmentProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, this::retrieveMeta);
        addDispatcher(ACTION_CONTENT, this::retrieveContent);
        addDispatcher(ACTION_UPLOAD, this::uploadContent);
        addDispatcher(ACTION_DELETE, this::deleteContent);
    }

    /*
     * This method gets the metadata for multiple attachments or for a specific
     * attachment with a specific sys_id value
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /api/now/api/now/attachment
     * - /api/now/api/now/attachment/{sys_id}
     */
    private void retrieveMeta(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
        final Class<?> model = getModel(in, tableName);
        final String sysId = in.getHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), String.class);

        Response response = ObjectHelper.isEmpty(sysId)
            ? client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path("attachment")
                .query(ServiceNowParams.SYSPARM_QUERY, in)
                .query(ServiceNowParams.SYSPARM_LIMIT, in)
                .query(ServiceNowParams.SYSPARM_OFFSET, in)
                .query(ServiceNowParams.SYSPARM_SUPPRESS_PAGINATION_HEADER, in)
                .invoke(HttpMethod.GET)
            : client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("attachment")
                .path(ObjectHelper.notNull(sysId, "sysId"))
                .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, model, response);
    }

    /*
     * This method gets the binary file attachment with a specific sys_id value.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /api/now/attachment/{sys_id}/file
     */
    private void retrieveContent(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
        final String sysId = in.getHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), String.class);

        Response response = client.reset()
            .type(MediaType.APPLICATION_JSON_TYPE)
            .accept("*/*")
            .path("now")
            .path("attachment")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("file")
            .invoke(HttpMethod.GET);

        // Header
        setHeaders(in, null, response);

        in.setBody(response.readEntity(InputStream.class));
    }

    /*
     * This method uploads a binary file specified in the request body as an attachment.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /api/now/api/now/attachment/file
     */
    private void uploadContent(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
        final Class<?> model = getModel(in, tableName);

        Response response = client.reset()
            .type(ObjectHelper.notNull(
                in.getHeader(ServiceNowConstants.CONTENT_TYPE, String.class),
                ServiceNowConstants.CONTENT_TYPE))
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path("attachment")
            .path("file")
            .query(ServiceNowParams.PARAM_FILE_NAME, in)
            .query(ServiceNowParams.PARAM_TABLE_NAME, in)
            .query(ServiceNowParams.PARAM_TABLE_SYS_ID, in)
            .query(ServiceNowParams.PARAM_ENCRYPTION_CONTEXT, in)
            .invoke(HttpMethod.POST, in.getMandatoryBody(InputStream.class));

        setBodyAndHeaders(in, model, response);
    }

    /*
     * This method deletes the attachment with a specific sys_id value.
     *
     * Method:
     * - DELETE
     *
     * URL Format:
     * - /api/now/attachment/{sys_id}
     */
    private void deleteContent(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = in.getHeader(ServiceNowParams.PARAM_TABLE_NAME.getHeader(), config.getTable(), String.class);
        final Class<?> model = getModel(in, tableName);
        final String sysId = in.getHeader(ServiceNowParams.PARAM_SYS_ID.getHeader(), String.class);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path("attachment")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .invoke(HttpMethod.DELETE);

        setBodyAndHeaders(in, model, response);
    }
}
