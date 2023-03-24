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
package org.apache.camel;

/**
 * This enum set various categories options into the UriEndpoint. This can be extended.
 */
public enum Category {
    DATAMINING("datamining"),
    AI("ai"),
    API("api"),
    AZURE("azure"),
    BATCH("batch"),
    BIGDATA("bigdata"),
    BITCOIN("bitcoin"),
    BLOCKCHAIN("blockchain"),
    CACHE("cache"),
    CHAT("chat"),
    CLOUD("cloud"),
    CLUSTERING("clustering"),
    CMS("cms"),
    COMPUTE("compute"),
    COMPUTING("computing"),
    CONTAINER("container"),
    CORE("core"),
    CRM("crm"),
    DATA("data"),
    DATABASE("database"),
    DATAGRID("datagrid"),
    DEEPLEARNING("deeplearning"),
    DEPLOYMENT("deployment"),
    DOCUMENT("document"),
    ENDPOINT("endpoint"),
    ENGINE("engine"),
    EVENTBUS("eventbus"),
    FILE("file"),
    HADOOP("hadoop"),
    HCM("hcm"),
    HL7("hl7"),
    HTTP("http"),
    IDENTITY("identity"),
    IOT("iot"),
    IPFS("ipfs"),
    JAVA("java"),
    LDAP("ldap"),
    LEDGER("ledger"),
    LOCATION("location"),
    LOG("log"),
    MAIL("mail"),
    MANAGEMENT("management"),
    MESSAGING("messaging"),
    MLLP("mllp"),
    MOBILE("mobile"),
    MONITORING("monitoring"),
    NETWORKING("networking"),
    NOSQL("nosql"),
    OPENAPI("openapi"),
    PAAS("paas"),
    PAYMENT("payment"),
    PLANNING("planning"),
    PRINTING("printing"),
    PROCESS("process"),
    QUEUE("queue"),
    REACTIVE("reactive"),
    REPORTING("reporting"),
    REST("rest"),
    RPC("rpc"),
    RSS("rss"),
    SAP("sap"),
    SCHEDULING("scheduling"),
    SCRIPT("script"),
    SEARCH("search"),
    SECURITY("security"),
    SERVERLESS("serverless"),
    SHEETS("sheets"),
    SOAP("soap"),
    SOCIAL("social"),
    SPRING("spring"),
    SQL("sql"),
    STREAMS("streams"),
    SUPPORT("support"),
    SWAGGER("swagger"),
    SYSTEM("system"),
    TCP("tcp"),
    TESTING("testing"),
    TRANSFORMATION("transformation"),
    UDP("udp"),
    VALIDATION("validation"),
    VOIP("voip"),
    WEBSERVICE("webservice"),
    WEBSOCKET("websocket"),
    WORKFLOW("workflow");

    private final String value;

    Category(final String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of this value
     *
     * @return Returns the string representation of this value
     */
    public String getValue() {
        return this.value;
    }
}
