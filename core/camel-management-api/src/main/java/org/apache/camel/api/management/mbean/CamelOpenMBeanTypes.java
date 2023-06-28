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
package org.apache.camel.api.management.mbean;

import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;

/**
 * Various JMX openmbean types used by Camel.
 */
public final class CamelOpenMBeanTypes {

    private CamelOpenMBeanTypes() {
    }

    public static TabularType listRestServicesTabularType() throws OpenDataException {
        CompositeType ct = listRestServicesCompositeType();
        return new TabularType(
                "listRestServices", "Lists all the rest services in the registry", ct, new String[] { "url", "method" });
    }

    public static CompositeType listRestServicesCompositeType() throws OpenDataException {
        return new CompositeType(
                "rests", "Rest Services",
                new String[] {
                        "url", "baseUrl", "basePath", "uriTemplate", "method", "consumes", "produces", "inType", "outType",
                        "state", "description" },
                new String[] {
                        "Url", "Base Url", "Base Path", "Uri Template", "Method", "Consumes", "Produces", "Input Type",
                        "Output Type", "State", "Description" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING,
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING });
    }

    public static TabularType listEndpointsTabularType() throws OpenDataException {
        CompositeType ct = listEndpointsCompositeType();
        return new TabularType("listEndpoints", "Lists all the endpoints in the registry", ct, new String[] { "url" });
    }

    public static CompositeType listEndpointsCompositeType() throws OpenDataException {
        return new CompositeType(
                "endpoints", "Endpoints",
                new String[] { "url", "static", "dynamic" },
                new String[] { "Url", "Static", "Dynamic" },
                new OpenType[] { SimpleType.STRING, SimpleType.BOOLEAN, SimpleType.BOOLEAN });
    }

    public static TabularType listExchangeFactoryTabularType() throws OpenDataException {
        CompositeType ct = listExchangeFactoryCompositeType();
        return new TabularType("listExchangeFactory", "Lists all the exchange factories", ct, new String[] { "url" });
    }

    public static CompositeType listExchangeFactoryCompositeType() throws OpenDataException {
        return new CompositeType(
                "factories", "Factories",
                new String[] { "url", "routeId", "capacity", "pooled", "created", "acquired", "released", "discarded" },
                new String[] { "Url", "RouteId", "Capacity", "Pooled", "Created", "Acquired", "Released", "Discarded" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.INTEGER, SimpleType.INTEGER, SimpleType.LONG,
                        SimpleType.LONG, SimpleType.LONG, SimpleType.LONG });
    }

    public static TabularType listRuntimeEndpointsTabularType() throws OpenDataException {
        CompositeType ct = listRuntimeEndpointsCompositeType();
        return new TabularType(
                "listRuntimeEndpoints", "Lists all the input and output endpoints gathered during runtime", ct,
                new String[] { "index" });
    }

    public static CompositeType listRuntimeEndpointsCompositeType() throws OpenDataException {
        return new CompositeType(
                "endpoints", "Endpoints",
                new String[] { "index", "url", "routeId", "direction", "static", "dynamic", "hits" },
                new String[] { "Index", "Url", "Route Id", "Direction", "Static", "Dynamic", "Hits" },
                new OpenType[] {
                        SimpleType.INTEGER, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN,
                        SimpleType.BOOLEAN, SimpleType.LONG });
    }

    public static TabularType listComponentsTabularType() throws OpenDataException {
        CompositeType ct = listComponentsCompositeType();
        return new TabularType("listComponents", "Lists all the components", ct, new String[] { "name" });
    }

    public static CompositeType listComponentsCompositeType() throws OpenDataException {
        return new CompositeType(
                "components", "Components",
                new String[] {
                        "name", "title", "syntax", "description", "label", "deprecated", "secret", "status", "type", "groupId",
                        "artifactId", "version" },
                new String[] {
                        "Name", "Title", "Syntax", "Description", "Label", "Deprecated", "Secret", "Status", "Type", "GroupId",
                        "ArtifactId", "Version" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING,
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING });
    }

    public static TabularType listAwaitThreadsTabularType() throws OpenDataException {
        CompositeType ct = listAwaitThreadsCompositeType();
        return new TabularType("listAwaitThreads", "Lists blocked threads by the routing engine", ct, new String[] { "id" });
    }

    public static CompositeType listAwaitThreadsCompositeType() throws OpenDataException {
        return new CompositeType(
                "threads", "Threads",
                new String[] { "id", "name", "exchangeId", "routeId", "nodeId", "duration" },
                new String[] { "Thread Id", "Thread name", "ExchangeId", "RouteId", "NodeId", "Duration" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING });
    }

    public static TabularType listEipsTabularType() throws OpenDataException {
        CompositeType ct = listEipsCompositeType();
        return new TabularType("listEips", "Lists all the EIPs", ct, new String[] { "name" });
    }

    public static CompositeType listEipsCompositeType() throws OpenDataException {
        return new CompositeType(
                "eips", "EIPs",
                new String[] { "name", "title", "description", "label", "status", "type" },
                new String[] { "Name", "Title", "Description", "Label", "Status", "Type" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING });
    }

    public static TabularType listInflightExchangesTabularType() throws OpenDataException {
        CompositeType ct = listInflightExchangesCompositeType();
        return new TabularType("listInflightExchanges", "Lists inflight exchanges", ct, new String[] { "exchangeId" });
    }

    public static CompositeType listInflightExchangesCompositeType() throws OpenDataException {
        return new CompositeType(
                "exchanges", "Exchanges",
                new String[] { "exchangeId", "fromRouteId", "routeId", "nodeId", "elapsed", "duration" },
                new String[] { "Exchange Id", "From RouteId", "RouteId", "NodeId", "Elapsed", "Duration" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.STRING });
    }

    public static TabularType choiceTabularType() throws OpenDataException {
        CompositeType ct = choiceCompositeType();
        return new TabularType("choice", "Choice statistics", ct, new String[] { "predicate" });
    }

    public static CompositeType choiceCompositeType() throws OpenDataException {
        return new CompositeType(
                "predicates", "Predicates",
                new String[] { "predicate", "language", "matches" },
                new String[] { "Predicate", "Language", "Matches" },
                new OpenType[] { SimpleType.STRING, SimpleType.STRING, SimpleType.LONG });
    }

    public static TabularType doTryTabularType() throws OpenDataException {
        CompositeType ct = doTryCompositeType();
        return new TabularType("doTry", "doTry statistics", ct, new String[] { "exception" });
    }

    public static CompositeType doTryCompositeType() throws OpenDataException {
        return new CompositeType(
                "exceptions", "Exception types",
                new String[] { "exception", "predicate", "language", "matches" },
                new String[] { "Exception", "Predicate", "Language", "Matches" },
                new OpenType[] { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.LONG });
    }

    public static TabularType loadbalancerExceptionsTabularType() throws OpenDataException {
        CompositeType ct = loadbalancerExceptionsCompositeType();
        return new TabularType("exception", "Exception statistics", ct, new String[] { "exception" });
    }

    public static CompositeType loadbalancerExceptionsCompositeType() throws OpenDataException {
        return new CompositeType(
                "exceptions", "Exceptions",
                new String[] { "exception", "failures" },
                new String[] { "Exception", "Failures" },
                new OpenType[] { SimpleType.STRING, SimpleType.LONG });
    }

    public static TabularType endpointsUtilizationTabularType() throws OpenDataException {
        CompositeType ct = endpointsUtilizationCompositeType();
        return new TabularType("endpointsUtilization", "Endpoint utilization statistics", ct, new String[] { "url" });
    }

    public static CompositeType endpointsUtilizationCompositeType() throws OpenDataException {
        return new CompositeType(
                "endpoints", "Endpoints",
                new String[] { "url", "hits" },
                new String[] { "Url", "Hits" },
                new OpenType[] { SimpleType.STRING, SimpleType.LONG });
    }

    public static TabularType listTransformersTabularType() throws OpenDataException {
        CompositeType ct = listTransformersCompositeType();
        return new TabularType(
                "listTransformers", "Lists all the transformers in the registry", ct, new String[] { "name", "from", "to" });
    }

    public static CompositeType listTransformersCompositeType() throws OpenDataException {
        return new CompositeType(
                "transformers", "Transformers",
                new String[] { "name", "from", "to", "static", "dynamic", "description" },
                new String[] { "Name", "From", "To", "Static", "Dynamic", "Description" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                        SimpleType.BOOLEAN, SimpleType.BOOLEAN, SimpleType.STRING });
    }

    public static TabularType listValidatorsTabularType() throws OpenDataException {
        CompositeType ct = listValidatorsCompositeType();
        return new TabularType("listValidators", "Lists all the validators in the registry", ct, new String[] { "type" });
    }

    public static CompositeType listValidatorsCompositeType() throws OpenDataException {
        return new CompositeType(
                "validators", "Validators",
                new String[] { "type", "static", "dynamic", "description" },
                new String[] { "Type", "Static", "Dynamic", "Description" },
                new OpenType[] { SimpleType.STRING, SimpleType.BOOLEAN, SimpleType.BOOLEAN, SimpleType.STRING });
    }

    public static CompositeType camelHealthDetailsCompositeType() throws OpenDataException {
        return new CompositeType(
                "healthDetails", "Health Details",
                new String[] {
                        "id", "group", "state", "enabled", "message", "failureUri", "failureCount", "failureStackTrace",
                        "readiness", "liveness" },
                new String[] {
                        "ID", "Group", "State", "Enabled", "Message", "Failure Uri", "Failure Count", "Failure StackTrace",
                        "Readiness", "Liveness" },
                new OpenType[] {
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.BOOLEAN, SimpleType.STRING,
                        SimpleType.STRING, SimpleType.INTEGER, SimpleType.STRING, SimpleType.BOOLEAN, SimpleType.BOOLEAN });
    }

    public static TabularType camelHealthDetailsTabularType() throws OpenDataException {
        CompositeType ct = camelHealthDetailsCompositeType();
        return new TabularType("healthDetails", "Health Details", ct, new String[] { "id" });
    }

    public static CompositeType camelRoutePropertiesCompositeType() throws OpenDataException {
        return new CompositeType(
                "routeProperties", "Route Properties",
                new String[] { "key", "value" },
                new String[] { "Key", "Value" },
                new OpenType[] { SimpleType.STRING, SimpleType.STRING });
    }

    public static TabularType camelRoutePropertiesTabularType() throws OpenDataException {
        CompositeType ct = camelRoutePropertiesCompositeType();
        return new TabularType("routeProperties", "Route Properties", ct, new String[] { "key" });
    }

    public static TabularType supervisingRouteControllerRouteStatusTabularType() throws OpenDataException {
        CompositeType ct = supervisingRouteControllerRouteStatusCompositeType();
        return new TabularType(
                "routeStatus", "Lists detailed status about all the routes (incl failure details for routes failed to start)",
                ct, new String[] { "index" });
    }

    public static CompositeType supervisingRouteControllerRouteStatusCompositeType() throws OpenDataException {
        return new CompositeType(
                "routes", "Routes",
                new String[] {
                        "index", "routeId", "status", "supervising", "attempts", "elapsed", "last", "error", "stacktrace" },
                new String[] {
                        "Index", "Route Id", "Status", "Supervising", "Attempts", "Elapsed", "Since Last Attempt", "Error",
                        "Stacktrace" },
                new OpenType[] {
                        SimpleType.INTEGER, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.LONG,
                        SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.STRING });
    }

}
