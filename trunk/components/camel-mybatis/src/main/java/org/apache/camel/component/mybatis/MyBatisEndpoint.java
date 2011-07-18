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
import org.apache.camel.util.ObjectHelper;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * @version 
 */
public class MyBatisEndpoint extends DefaultPollingEndpoint {

    private MyBatisProcessingStrategy processingStrategy;
    private String statement;
    private StatementType statementType;
    private int maxMessagesPerPoll;

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

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public void setStatementType(StatementType statementType) {
        this.statementType = statementType;
    }

    public synchronized MyBatisProcessingStrategy getProcessingStrategy() {
        if (processingStrategy == null) {
            processingStrategy = new DefaultMyBatisProcessingStrategy();
        }
        return processingStrategy;
    }

    public void setProcessingStrategy(MyBatisProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

}
