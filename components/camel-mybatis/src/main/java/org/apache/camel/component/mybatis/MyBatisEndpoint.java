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
package org.apache.camel.component.mybatis;

import java.io.IOException;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Performs a query, poll, insert, update or delete in a relational database using MyBatis.
 */
@UriEndpoint(firstVersion = "2.7.0", scheme = "mybatis", title = "MyBatis", syntax = "mybatis:statement", consumerClass =  MyBatisConsumer.class, label = "database,sql")
public class MyBatisEndpoint extends DefaultPollingEndpoint {

    @UriPath @Metadata(required = "true")
    private String statement;
    @UriParam(label = "producer")
    private StatementType statementType;
    @UriParam(label = "consumer", description = "Enables or disables transaction. If enabled then if processing an exchange failed then the consumer"
            + "break out processing any further exchanges to cause a rollback eager.")
    private boolean transacted;
    @UriParam(label = "consumer", defaultValue = "0")
    private int maxMessagesPerPoll;
    @UriParam
    private String outputHeader;
    @UriParam(label = "consumer")
    private String inputHeader;
    @UriParam(label = "consumer", optionalPrefix = "consumer.")
    private String onConsume;
    @UriParam(label = "consumer", optionalPrefix = "consumer.", defaultValue = "true")
    private boolean useIterator = true;
    @UriParam(label = "consumer", optionalPrefix = "consumer.")
    private boolean routeEmptyResultSet;
    @UriParam(label = "consumer,advanced")
    private MyBatisProcessingStrategy processingStrategy = new DefaultMyBatisProcessingStrategy();
    @UriParam(label = "producer", defaultValue = "SIMPLE")
    private ExecutorType executorType;

    public MyBatisEndpoint() {
    }

    public MyBatisEndpoint(String endpointUri, Component component, String statement) {
        super(endpointUri, component);
        this.statement = statement;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(statementType, "statementType", this);
        ObjectHelper.notNull(statement, "statement", this);
        return new MyBatisProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(statement, "statement", this);
        MyBatisConsumer consumer = new MyBatisConsumer(this, processor);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public MyBatisComponent getComponent() {
        return (MyBatisComponent) super.getComponent();
    }

    public SqlSessionFactory getSqlSessionFactory() throws IOException {
        return getComponent().getSqlSessionFactory();
    }

    public String getStatement() {
        return statement;
    }

    /**
     * The statement name in the MyBatis XML mapping file which maps to the query, insert, update or delete operation you wish to evaluate.
     */
    public void setStatement(String statement) {
        this.statement = statement;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    /**
     * Mandatory to specify for the producer to control which kind of operation to invoke.
     */
    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
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

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Enables or disables transaction. If enabled then if processing an exchange failed then the consumer
     + break out processing any further exchanges to cause a rollback eager
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

    public MyBatisProcessingStrategy getProcessingStrategy() {
        return processingStrategy;
    }

    /**
     * To use a custom MyBatisProcessingStrategy
     */
    public void setProcessingStrategy(MyBatisProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * This option is intended to split results returned by the database pool into the batches and deliver them in multiple exchanges.
     * This integer defines the maximum messages to deliver in single exchange. By default, no maximum is set.
     * Can be used to set a limit of e.g. 1000 to avoid when starting up the server that there are thousands of files.
     * Set a value of 0 or negative to disable it.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
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

    public String getOnConsume() {
        return onConsume;
    }

    /**
     * Statement to run after data has been processed in the route
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public boolean isUseIterator() {
        return useIterator;
    }

    /**
     * Process resultset individually or as a list
     */
    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    public boolean isRouteEmptyResultSet() {
        return routeEmptyResultSet;
    }

    /**
     * Whether allow empty resultset to be routed to the next hop
     */
    public void setRouteEmptyResultSet(boolean routeEmptyResultSet) {
        this.routeEmptyResultSet = routeEmptyResultSet;
    }
}
