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

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.region.FunctionGraphRegion;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Invoke serverless functions in Huawei Cloud FunctionGraph.
 */
@UriEndpoint(firstVersion = "3.11.0", scheme = "hwcloud-functiongraph", title = "Huawei FunctionGraph",
             syntax = "hwcloud-functiongraph:operation",
             category = { Category.CLOUD, Category.SERVERLESS }, producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = { Carrier.MAP, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "The event of the function, which FunctionGraph takes as a JSON object: a Map,"
                                                + " or a JSON object as text, bytes, a stream or a file. A JSON array or another"
                                                + " JSON value fails: wrap it in a JSON object. A POJO fails unless its text is"
                                                + " JSON: marshal it to JSON first. No body fails"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL },
                                   description = "The result of the function as text. When the result is a JSON object with a"
                                                 + " top-level body field, such as the result of a function behind an API Gateway"
                                                 + " trigger, the body field: a string as it is, an object or an array as JSON,"
                                                 + " and no body when the field is null. Otherwise the whole result as it is. No"
                                                 + " body when the function gives no result. With the CamelHwCloudFgXCffLogType"
                                                 + " exchange property set, the log of the call is in the CamelHwCloudFgXCffLogs"
                                                 + " exchange property"))
public class FunctionGraphEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. Only invokeFunction is supported. The"
                           + " CamelHwCloudFgOperation exchange property replaces this option.",
             displayName = "Operation", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of FunctionGraph, for example cn-north-4. The producer uses it in the URN of the"
                            + " function, and to build the client when the endpoint option is not set.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The ID of the Huawei Cloud project of the function. The producer uses it in the URN of the"
                            + " function and in the credentials of the requests.",
              displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "The package of the function, which is part of the URN of the function. The"
                            + " CamelHwCloudFgPackage exchange property replaces this option.",
              displayName = "Function package", defaultValue = FunctionGraphConstants.DEFAULT_FUNCTION_PACKAGE)
    @Metadata(required = false)
    private String functionPackage;

    @UriParam(description = "The name of the function to invoke. The producer always invokes the latest version of the"
                            + " function. The CamelHwCloudFgFunction exchange property replaces this option, and one of"
                            + " them must be set.",
              displayName = "Function name")
    @Metadata(required = false)
    private String functionName;

    @UriParam(description = "The host name or IP address of the proxy server. The producer uses a proxy only when both this"
                            + " option and the proxyPort option are set.",
              displayName = "Proxy server host", label = "proxy")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "The port of the proxy server. The producer uses a proxy only when both this option and the"
                            + " proxyHost option are set.",
              displayName = "Proxy server port", label = "proxy")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "The user name to authenticate with the proxy server. It is used only when the proxyHost and"
                            + " proxyPort options are set.",
              displayName = "Proxy user", security = "secret", label = "proxy")
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "The password to authenticate with the proxy server. It is used only when the proxyUser option"
                            + " is set.",
              displayName = "Proxy password", security = "secret",
              label = "proxy")
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "Whether to skip the verification of the TLS certificate of the server. This makes the connection"
                            + " insecure: use it only for testing.",
              displayName = "SSL Verification Ignored",
              defaultValue = "false", label = "security", security = "insecure:ssl")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    @UriParam(description = "The endpoint URL of FunctionGraph, for example https://functiongraph.cn-north-4.myhuaweicloud.com."
                            + " When set, the client uses it instead of the endpoint of the region option. The region option"
                            + " is still needed for the URN of the function.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "The AK/SK pair as an org.apache.camel.component.huaweicloud.common.models.ServiceKeys. When set,"
                            + " its accessKey and secretKey replace the accessKey and secretKey options.",
              displayName = "Service Configuration",
              security = "secret")
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "The access key ID (AK) of the Huawei Cloud credentials. It is not used when the serviceKeys"
                            + " option is set.",
              displayName = "API access key (AK)", security = "secret",
              label = "security")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "The secret access key (SK) of the Huawei Cloud credentials. It is not used when the serviceKeys"
                            + " option is set.",
              displayName = "API secret key (SK)", security = "secret",
              label = "security")
    @Metadata(required = true)
    private String secretKey;

    private FunctionGraphClient functionGraphClient;

    public FunctionGraphEndpoint() {
    }

    public FunctionGraphEndpoint(String uri, String operation, FunctionGraphComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    public Producer createProducer() throws Exception {
        return new FunctionGraphProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getFunctionPackage() {
        return functionPackage;
    }

    public void setFunctionPackage(String functionPackage) {
        this.functionPackage = functionPackage;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public FunctionGraphClient getFunctionGraphClient() {
        return functionGraphClient;
    }

    public void setFunctionGraphClient(FunctionGraphClient functionGraphClient) {
        this.functionGraphClient = functionGraphClient;
    }

    /**
     * Initialize and return a new FunctionGraph Client
     *
     * @return
     */
    public FunctionGraphClient initClient() {
        if (functionGraphClient != null) {
            return functionGraphClient;
        }

        // setup AK/SK credential information. User can input AK/SK through the ServiceKeys class, which, if provided, overrides the AK/SK passed through the endpoint
        BasicCredentials auth = new BasicCredentials()
                .withAk(getServiceKeys() != null
                        ? getServiceKeys().getAccessKey()
                        : getAccessKey())
                .withSk(getServiceKeys() != null
                        ? getServiceKeys().getSecretKey()
                        : getSecretKey())
                .withProjectId(getProjectId());

        // setup http information (including proxy information if provided)
        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig();
        httpConfig.withIgnoreSSLVerification(isIgnoreSslVerification());
        if (ObjectHelper.isNotEmpty(getProxyHost())
                && getProxyPort() > 0) {
            httpConfig.withProxyHost(getProxyHost())
                    .withProxyPort(getProxyPort());

            if (ObjectHelper.isNotEmpty(getProxyUser())) {
                httpConfig.withProxyUsername(getProxyUser());
                if (ObjectHelper.isNotEmpty(getProxyPassword())) {
                    httpConfig.withProxyPassword(getProxyPassword());
                }
            }
        }

        // if an endpoint url is provided, the FunctionGraphClient will be built with an endpoint. Otherwise, it will be built with the provided region
        if (ObjectHelper.isNotEmpty(getEndpoint())) {
            return FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withEndpoint(getEndpoint())
                    .build();
        } else {
            return FunctionGraphClient.newBuilder()
                    .withCredential(auth)
                    .withHttpConfig(httpConfig)
                    .withRegion(FunctionGraphRegion.valueOf(getRegion()))
                    .build();
        }
    }

}
