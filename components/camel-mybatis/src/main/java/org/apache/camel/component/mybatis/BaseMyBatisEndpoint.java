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

import org.apache.camel.Component;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;

public abstract class BaseMyBatisEndpoint extends DefaultPollingEndpoint {

    private SqlSessionFactory sqlSessionFactory;

    @UriParam(label = "producer", defaultValue = "SIMPLE",
              description = "The MyBatis executor type of the session that the producer opens for each message: SIMPLE"
                            + " prepares a new statement for each call, REUSE reuses the prepared statements, and BATCH"
                            + " batches the updates and sends them together when the session is committed. With BATCH, MyBatis"
                            + " returns a fixed placeholder value instead of the count of rows of an insert, update or delete."
                            + " If not"
                            + " set, the defaultExecutorType of the MyBatis configuration is used.")
    private ExecutorType executorType;
    @UriParam(label = "producer",
              description = "The name of the header that holds the parameter of the statement, read instead of the message"
                            + " body. If not set, the producer reads the message body.")
    private String inputHeader;
    @UriParam(description = "The name of the header to store the result in, instead of the message body, which then stays"
                            + " unchanged. The producer then does not set the CamelMyBatisResult header, and the consumer"
                            + " puts each polled row, or the List of rows, in this header and leaves the body empty.")
    private String outputHeader;

    protected BaseMyBatisEndpoint() {
    }

    protected BaseMyBatisEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public MyBatisComponent getComponent() {
        return (MyBatisComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (sqlSessionFactory == null) {
            sqlSessionFactory = getComponent().createSqlSessionFactory();
        }
    }

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    public ExecutorType getExecutorType() {
        return executorType;
    }

    public void setExecutorType(ExecutorType executorType) {
        this.executorType = executorType;
    }

    public void setExecutorType(String executorType) {
        this.executorType = ExecutorType.valueOf(executorType.toUpperCase());
    }

    public String getInputHeader() {
        return inputHeader;
    }

    public void setInputHeader(String inputHeader) {
        this.inputHeader = inputHeader;
    }

    public String getOutputHeader() {
        return outputHeader;
    }

    public void setOutputHeader(String outputHeader) {
        this.outputHeader = outputHeader;
    }

}
