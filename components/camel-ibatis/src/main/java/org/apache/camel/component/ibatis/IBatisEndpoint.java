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
package org.apache.camel.component.ibatis;

import java.io.IOException;

import com.ibatis.sqlmap.client.SqlMapClient;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ibatis.strategy.DefaultIBatisProcessingStategy;
import org.apache.camel.component.ibatis.strategy.IBatisProcessingStrategy;
import org.apache.camel.component.ibatis.strategy.TransactionIsolationLevel;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Performs a query, poll, insert, update or delete in a relational database using Apache iBATIS.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "ibatis", title = "iBatis", syntax = "ibatis:statement", consumerClass = IBatisConsumer.class, label = "database,sql")
public class IBatisEndpoint extends DefaultPollingEndpoint {
    @UriPath @Metadata(required = "true")
    private String statement;
    @UriParam(defaultValue = "true")
    private boolean useTransactions = true;
    @UriParam(label = "producer")
    private StatementType statementType;
    @UriParam(label = "consumer", defaultValue = "0")
    private int maxMessagesPerPoll;
    @UriParam(label = "consumer")
    private IBatisProcessingStrategy strategy;
    @UriParam(defaultValue = "TRANSACTION_REPEATABLE_READ",
              enums = "TRANSACTION_NONE,TRANSACTION_READ_UNCOMMITTED,TRANSACTION_READ_COMMITTED,TRANSACTION_REPEATABLE_READ,TRANSACTION_SERIALIZABLE")
    private String isolation;
    @UriParam(label = "consumer", optionalPrefix = "consumer.")
    private String onConsume;
    @UriParam(label = "consumer", optionalPrefix = "consumer.", defaultValue = "true")
    private boolean useIterator = true;
    @UriParam(label = "consumer", optionalPrefix = "consumer.")
    private boolean routeEmptyResultSet;

    public IBatisEndpoint() {
    }

    public IBatisEndpoint(String uri, IBatisComponent component, String statement) throws Exception {
        super(uri, component);
        setUseTransactions(component.isUseTransactions());
        setStatement(statement);
    }

    @Override
    public IBatisComponent getComponent() {
        return (IBatisComponent) super.getComponent();
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(statementType, "statementType", this);
        return new IBatisProducer(this);
    }

    @Override
    public IBatisConsumer createConsumer(Processor processor) throws Exception {
        IBatisConsumer consumer = new IBatisConsumer(this, processor);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    public SqlMapClient getSqlMapClient() throws IOException {
        return getComponent().getSqlMapClient();
    }

    public IBatisProcessingStrategy getProcessingStrategy() throws Exception {
        return strategy;
    }

    /**
     * Allows to plugin a custom {@link IBatisProcessingStrategy} to use by the consumer.
     */
    public void setStrategy(IBatisProcessingStrategy strategy) {
        this.strategy = strategy;
    }

    public String getStatement() {
        return statement;
    }

    /**
     * The statement name in the iBatis XML mapping file which maps to the query, insert, update or delete operation you wish to evaluate.
     */
    public void setStatement(String statement) {
        this.statement = statement;
    }

    public boolean isUseTransactions() {
        return useTransactions;
    }

    /**
     * Whether to use transactions.
     * <p/>
     * This option is by default true.
     */
    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
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

    public String getIsolation() throws Exception {
        return isolation;
    }

    /**
     * Transaction isolation level
     */
    public void setIsolation(String isolation) throws Exception {
        this.isolation = isolation;
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (strategy == null) {
            strategy = new DefaultIBatisProcessingStategy();
        }
        if (isolation != null) {
            strategy.setIsolation(TransactionIsolationLevel.intValueOf(isolation));
        }
    }
}
