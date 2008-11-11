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
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.ibatis.sqlmap.client.SqlMapClient;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ibatis.strategy.DefaultIBatisProcessingStategy;
import org.apache.camel.component.ibatis.strategy.IBatisProcessingStrategy;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An <a href="http://activemq.apache.org/camel/ibatis.html>iBatis Endpoint</a>
 * for performing SQL operations using an XML mapping file to abstract away the SQL
 *
 * @version $Revision$
 */
public class IBatisEndpoint extends DefaultPollingEndpoint {
    private static final transient Log logger = LogFactory.getLog(IBatisEndpoint.class);

    private IBatisProcessingStrategy strategy;
    /**
     * Indicates if transactions are necessary.  Defaulted in IBatisComponent.
     */
    private boolean useTransactions;
    /**
     * Statement to run when polling or processing
     */
    private String statement;
    /**
     * Name of a strategy to use for dealing w/
     * polling a database and consuming the message.  Can be a bean name
     * or a class name.
     */
    private String consumeStrategyName;
    /**
     * URI parameters
     */
    private Map params;

    public IBatisEndpoint(String uri, IBatisComponent component, 
            String statement, Map params) throws Exception {

        super(uri, component);
        this.params = params;
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
        return new IBatisProducer(this);
    }

    @Override
    public IBatisPollingConsumer createConsumer(Processor processor) throws Exception {
        IBatisPollingConsumer consumer = new IBatisPollingConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }
/*
    @Override
    public PollingConsumer<Exchange> createPollingConsumer() throws Exception {
        return new IBatisPollingConsumer(this);
    }
*/
    /**
     * @return SqlMapClient
     * @throws IOException if the component is configured with a SqlMapConfig
     * and there is a problem reading the file
     */
    public SqlMapClient getSqlMapClient() throws IOException {
        return getComponent().getSqlMapClient();
    }

    /**
     * Gets the IbatisProcessingStrategy to to use when consuming messages+        * from the database
     * @return IbatisProcessingStrategy
     * @throws Exception
     */
    public IBatisProcessingStrategy getProcessingStrategy() throws Exception {
        if (strategy == null) {
            String strategyName = (String) params.get("consumeStrategy");
            strategy = getStrategy(strategyName, new DefaultIBatisProcessingStategy());
        }
        return strategy;
    }

    /**
     * Statement to run when polling or processing
     * @return name of the statement
    */
    public String getStatement() {
        return statement;
    }
    
    /**
     * Statement to run when polling or processing
     * @param statement
     */
    public void setStatement(String statement) {
        this.statement = statement;
    }

    /**
     * Resolves a strategy in the camelContext or by class name
     * @param name
     * @param defaultStrategy
     * @return IbatisProcessingStrategy
     * @throws Exception
     */
    private IBatisProcessingStrategy getStrategy(String name, IBatisProcessingStrategy defaultStrategy) throws Exception {

        if (name == null) {
            return defaultStrategy;
        }

        IBatisProcessingStrategy strategy = getComponent().getCamelContext().getRegistry().lookup(name, IBatisProcessingStrategy.class);
        if (strategy == null) {
            try {
                Class<?> clazz = ObjectHelper.loadClass(name);
                if (clazz != null) {
                    strategy = ObjectHelper.newInstance(clazz, IBatisProcessingStrategy.class);
                }
            } catch(Exception e) {
                logger.error("Failed to resolve/create processing strategy (" + name + ")", e);
                throw e;
            }
        }
        
        return strategy != null ? strategy : defaultStrategy;
    }

    /**
     * Indicates if transactions should be used when calling statements.  Useful if using a comma separated list when
     * consuming records.
     * @return boolean
     */
    public boolean isUseTransactions() {
        return useTransactions;
    }

    /**
     * Sets indicator to use transactions for consuming and error handling statements.
     * @param useTransactions
     */
    public void setUseTransactions(boolean useTransactions) {
        this.useTransactions = useTransactions;
    }

    public String getConsumeStrategyName() {
        return consumeStrategyName;
    }
    
    public void setConsumeStrategyName(String consumeStrategyName) {
        this.consumeStrategyName = consumeStrategyName;
    }
}
