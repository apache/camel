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
package org.apache.camel.component.servicenow.model;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.component.servicenow.ServiceNowException;
import org.apache.cxf.jaxrs.ext.PATCH;

@Path("/table")
@Produces("application/json")
@Consumes("application/json")
public interface ServiceNowTable {

    @GET
    @Path("{tableName}")
    JsonNode retrieveRecord(
        @PathParam("tableName") String tableName,
        @QueryParam("sysparm_query") String query,
        @QueryParam("sysparm_display_value") String displayValue,
        @QueryParam("sysparm_exclude_reference_link") Boolean excludeReferenceLink,
        @QueryParam("sysparm_fields") String fields,
        @QueryParam("sysparm_limit") Integer limit,
        @QueryParam("sysparm_view") String view
    ) throws ServiceNowException;

    @GET
    @Path("{tableName}/{sysId}")
    JsonNode retrieveRecordById(
        @PathParam("tableName") String tableName,
        @PathParam("sysId") String id,
        @QueryParam("sysparm_display_value") String displayValue,
        @QueryParam("sysparm_exclude_reference_link") Boolean excludeReferenceLink,
        @QueryParam("sysparm_fields") String fields,
        @QueryParam("sysparm_view") String view
    ) throws ServiceNowException;

    @POST
    @Path("{tableName}")
    JsonNode createRecord(
        @PathParam("tableName") String tableName,
        @QueryParam("sysparm_display_value") String displayValue,
        @QueryParam("sysparm_exclude_reference_link") Boolean excludeReferenceLink,
        @QueryParam("sysparm_fields") String fields,
        @QueryParam("sysparm_input_display_value") Boolean inputDisplayValue,
        @QueryParam("sysparm_suppress_auto_sys_field") Boolean suppressAutoSysField,
        @QueryParam("sysparm_view") String view,
        String body
    ) throws ServiceNowException;

    @PUT
    @Path("{tableName}/{sysId}")
    JsonNode modifyRecord(
        @PathParam("tableName") String tableName,
        @PathParam("sysId") String id,
        @QueryParam("sysparm_display_value") String displayValue,
        @QueryParam("sysparm_exclude_reference_link") Boolean excludeReferenceLink,
        @QueryParam("sysparm_fields") String fields,
        @QueryParam("sysparm_input_display_value") Boolean inputDisplayValue,
        @QueryParam("sysparm_suppress_auto_sys_field") Boolean suppressAutoSysField,
        @QueryParam("sysparm_view") String view,
        String body
    ) throws ServiceNowException;

    @DELETE
    @Path("{tableName}/{sysId}")
    JsonNode deleteRecord(
        @PathParam("tableName") String tableName,
        @PathParam("sysId") String id
    ) throws ServiceNowException;

    @PATCH
    @Path("{tableName}/{sysId}")
    JsonNode updateRecord(
        @PathParam("tableName") String tableName,
        @PathParam("sysId") String id,
        @QueryParam("sysparm_display_value") String displayValue,
        @QueryParam("sysparm_exclude_reference_link") Boolean excludeReferenceLink,
        @QueryParam("sysparm_fields") String fields,
        @QueryParam("sysparm_input_display_value") Boolean inputDisplayValue,
        @QueryParam("sysparm_suppress_auto_sys_field") Boolean suppressAutoSysField,
        @QueryParam("sysparm_view") String view,
        String body
    ) throws ServiceNowException;
}
