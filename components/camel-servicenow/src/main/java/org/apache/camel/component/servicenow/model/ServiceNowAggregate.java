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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.component.servicenow.ServiceNowException;

@Path("/stats")
@Produces("application/json")
@Consumes("application/json")
public interface ServiceNowAggregate {

    @GET
    @Path("{tableName}")
    JsonNode retrieveStats(
        @PathParam("tableName") String tableName,
        @QueryParam("sysparm_query") String query,
        @QueryParam("sysparm_avg_fields") String avgFields,
        @QueryParam("sysparm_count") String count,
        @QueryParam("sysparm_min_fields") String minFields,
        @QueryParam("sysparm_max_fields") String maxFields,
        @QueryParam("sysparm_sum_fields") String sumFields,
        @QueryParam("sysparm_group_by") String groupBy,
        @QueryParam("sysparm_order_by") String orderBy,
        @QueryParam("sysparm_having") String having,
        @QueryParam("sysparm_display_value") String displayValue
    ) throws ServiceNowException;
}
