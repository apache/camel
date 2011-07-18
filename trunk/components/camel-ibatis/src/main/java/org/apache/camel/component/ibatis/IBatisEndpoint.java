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
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * An <a href="http://camel.apache.org/ibatis.html>iBatis Endpoint</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version 
 */
public class IBatisEndpoint extends DefaultPollingEndpoint {
    private IBatisProcessingStrategy strategy;
    private boolean useTransactions;
    private String statement;
    private StatementType statementType;
    private int maxMessagesPerPoll;

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
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Gets the iBatis SqlMapClient
     */
    public SqlMapClient getSqlMapClient() throws IOException {
        return getComponent().getSqlMapClient();
    }

    /**
     * Gets the IbatisProcessingStrategy to to use when consuming messages from the database
     */
    public IBatisProcessingStrategy getProcessingStrategy() throws Exception {
        if (strategy == null) {
            strategy = new DefaultIBatisProcessingStategy();
        }
        return strategy;
    }

    public void setStrategy(IBatisProcessingStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Statement to run when polling or processing
    */
    public String getStatement() {
        return statement;
    }
    
    /**
     * Statement to run when polling or processing
     */
    public void setStatement(String statement) {
        this.statement = statement;
    }

    /**
     * Indicates if transactions should be used when calling statements.  Useful if using a comma separated list when
     * consuming records.
     */
    public boolean isUseTransactions() {
        return useTransactions;
    }

    /**
     * Sets indicator to use transactions for consuming and error handling statements.
     */
    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
}
