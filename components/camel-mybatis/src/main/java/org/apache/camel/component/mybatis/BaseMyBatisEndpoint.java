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
package org.apache.camel.component.mybatis;

import java.io.IOException;

import org.apache.camel.Component;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;

public abstract class BaseMyBatisEndpoint extends DefaultPollingEndpoint {
    @UriParam(label = "producer", defaultValue = "SIMPLE")
    private ExecutorType executorType;
    @UriParam(label = "producer")
    private String inputHeader;
    @UriParam(label = "producer")
    private String outputHeader;

    public BaseMyBatisEndpoint() {
    }

    public BaseMyBatisEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public MyBatisComponent getComponent() {
        return (MyBatisComponent) super.getComponent();
    }

    public SqlSessionFactory getSqlSessionFactory() throws IOException {
        return getComponent().getSqlSessionFactory();
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    /**
     * The executor type to be used while executing statements.
     * <ul>
     *     <li>simple - executor does nothing special.</li>
     *     <li>reuse - executor reuses prepared statements.</li>
     *     <li>batch - executor reuses statements and batches updates.</li>
     * </ul>
     */
    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public void setExecutorType(String executorType) {
        this.executorType = ExecutorType.valueOf(executorType.toUpperCase());
    }

    public String getInputHeader() {
        return inputHeader;
    }

    /**
     * User the header value for input parameters instead of the message body.
     * By default, inputHeader == null and the input parameters are taken from the message body.
     * If outputHeader is set, the value is used and query parameters will be taken from the
     * header instead of the body.
     */
    public void setInputHeader(String inputHeader) {
        this.inputHeader = inputHeader;
    }

    public String getOutputHeader() {
        return outputHeader;
    }

    /**
     * Store the query result in a header instead of the message body.
     * By default, outputHeader == null and the query result is stored in the message body,
     * any existing content in the message body is discarded.
     * If outputHeader is set, the value is used as the name of the header to store the
     * query result and the original message body is preserved. Setting outputHeader will
     * also omit populating the default CamelMyBatisResult header since it would be the same
     * as outputHeader all the time.
     */
    public void setOutputHeader(String outputHeader) {
        this.outputHeader = outputHeader;
    }

}
