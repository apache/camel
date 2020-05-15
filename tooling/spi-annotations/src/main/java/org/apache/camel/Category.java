package org.apache.camel;

/**
 * This enum set various categories options into the UriEndpoint.
 * This can be extended.
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
    CHATSCRIPT("chatscript"),
    CLOUD("cloud"),
    CLUSTERING("clustering"),
    CMS("cms"),
    COMPUTE("compute"),
    COMPUTING("computing"),
    CONTAINER("container"),
    CORDA("corda"),
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
    MONGODB("mongodb"),
    MONITORING("monitoring"),
    MYSQL("mysql"),
    NETWORKING("networking"),
    NOSQL("nosql"),
    OPENAPI("openapi"),
    PAAS("paas"),
    PAYMENT("payment"),
    PLANNING("planning"),
    POSTGRES("postgres"),
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
    SQLSERVER("sqlserver"),
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
     * @return Returns the string representation of this value
     */
    public String getValue() {
        return this.value;
    }
}
