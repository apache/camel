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

import org.apache.camel.Exchange;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBatisBeanProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisBeanProducer.class);

    private final MyBatisBeanEndpoint endpoint;

    public MyBatisBeanProducer(MyBatisBeanEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SqlSession session;

        ExecutorType executorType = endpoint.getExecutorType();
        if (executorType == null) {
            session = endpoint.getSqlSessionFactory().openSession();
        } else {
            session = endpoint.getSqlSessionFactory().openSession(executorType);
        }
        LOG.debug("Opened MyBatis SqlSession: {}", session);

        try {
            doProcess(exchange, session);
            // flush the batch statements and commit the database connection
            session.commit();
        } catch (Exception e) {
            // discard the pending batch statements and roll the database connection back
            session.rollback();
            throw e;
        } finally {
            // and finally close the session as we're done
            LOG.debug("Closing MyBatis SqlSession: {}", session);
            session.close();
        }
    }

    protected void doProcess(Exchange exchange, SqlSession session) throws Exception {
        LOG.trace("Invoking MyBatisBean on {}:{}", endpoint.getBeanName(), endpoint.getMethodName());

        // if we use input or output header we need to copy exchange to avoid mutating the
        Exchange copy = ExchangeHelper.createCopy(exchange, true);

        Object input = getInput(copy);
        copy.getMessage().setBody(input);

        BeanProcessor beanProcessor = createBeanProcessor(session);
        beanProcessor.start();
        beanProcessor.process(copy);
        beanProcessor.stop();

        if (copy.getException() != null) {
            session.rollback();
            throw copy.getException();
        }

        Object result = copy.getMessage().getBody();
        if (result != input) {
            if (endpoint.getOutputHeader() != null) {
                // set the result as header for insert
                LOG.trace("Setting result as header [{}]: {}", endpoint.getOutputHeader(), result);
                exchange.getMessage().setHeader(endpoint.getOutputHeader(), result);
            } else {
                // set the result as body for insert
                LOG.trace("Setting result as body: {}", result);
                exchange.getMessage().setBody(result);
                exchange.getMessage().setHeader(MyBatisConstants.MYBATIS_RESULT, result);
            }
        }
    }

    private BeanProcessor createBeanProcessor(SqlSession session) throws Exception {
        // discover the bean and get the mapper
        // is the bean a alias type
        Class<?> clazz = session.getConfiguration().getTypeAliasRegistry().resolveAlias(endpoint.getBeanName());
        if (clazz == null) {
            // its maybe a FQN so try to use Camel to lookup the class
            clazz = getEndpoint().getCamelContext().getClassResolver().resolveMandatoryClass(endpoint.getBeanName());
        }

        LOG.debug("Resolved MyBatis Bean: {} as class: {}", endpoint.getBeanName(), clazz);

        // find the mapper
        Object mapper = session.getMapper(clazz);
        if (mapper == null) {
            throw new IllegalArgumentException(
                    "No Mapper with typeAlias or class name: " + endpoint.getBeanName() + " in MyBatis configuration.");
        }
        LOG.debug("Resolved MyBatis Bean mapper: {}", mapper);

        BeanProcessor answer = new BeanProcessor(mapper, getEndpoint().getCamelContext());
        answer.setMethod(endpoint.getMethodName());
        return answer;
    }

    private Object getInput(final Exchange exchange) {
        final String inputHeader = endpoint.getInputHeader();
        if (inputHeader != null) {
            return exchange.getIn().getHeader(inputHeader);
        } else {
            return exchange.getIn().getBody();
        }
    }

}
