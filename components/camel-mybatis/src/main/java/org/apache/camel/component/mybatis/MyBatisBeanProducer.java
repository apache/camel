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

import org.apache.camel.Exchange;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBatisBeanProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MyBatisBeanProducer.class);
    private MyBatisBeanEndpoint endpoint;
    private BeanProcessor beanProcessor;
    private SqlSession session;

    public MyBatisBeanProducer(MyBatisBeanEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        LOG.trace("Invoking MyBatisBean on {}:{}", endpoint.getBeanName(), endpoint.getMethodName());
        beanProcessor.process(exchange);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // discover the bean and get the mapper
        session = null;

        ExecutorType executorType = endpoint.getExecutorType();
        if (executorType == null) {
            session = endpoint.getSqlSessionFactory().openSession();
        } else {
            session = endpoint.getSqlSessionFactory().openSession(executorType);
        }
        LOG.debug("Opened MyBatis SqlSession: {}", session);

        // is the bean a alias type
        Class clazz = session.getConfiguration().getTypeAliasRegistry().resolveAlias(endpoint.getBeanName());
        if (clazz == null) {
            // its maybe a FQN so try to use Camel to lookup the class
            clazz = getEndpoint().getCamelContext().getClassResolver().resolveMandatoryClass(endpoint.getBeanName());
        }

        LOG.debug("Resolved MyBatis Bean: {} as class: {}", endpoint.getBeanName(), clazz);

        // find the mapper
        Object mapper = session.getMapper(clazz);
        if (mapper == null) {
            throw new IllegalArgumentException("No Mapper with typeAlias or class name: " + endpoint.getBeanName() + " in MyBatis configuration.");
        }
        LOG.debug("Resolved MyBatis Bean mapper: {}", mapper);

        beanProcessor = new BeanProcessor(mapper, getEndpoint().getCamelContext());
        beanProcessor.setMethod(endpoint.getMethodName());
        ServiceHelper.startService(beanProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(beanProcessor);

        LOG.debug("Closing MyBatis SqlSession: {}", session);
        IOHelper.close(session);
        session = null;
    }
}
