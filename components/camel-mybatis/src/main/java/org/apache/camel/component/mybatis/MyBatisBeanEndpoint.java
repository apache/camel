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
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * Performs a query, insert, update or delete in a relational database using MyBatis.
 */
@UriEndpoint(firstVersion = "2.22.0", scheme = "mybatis-bean", title = "MyBatis Bean", syntax = "mybatis-bean:beanName:methodName", producerOnly = true, label = "database,sql")
public class MyBatisBeanEndpoint extends BaseMyBatisEndpoint {

    @UriPath @Metadata(required = true)
    private String beanName;
    @UriPath @Metadata(required = true)
    private String methodName;

    public MyBatisBeanEndpoint() {
    }

    public MyBatisBeanEndpoint(String endpointUri, Component component, String beanName, String methodName) {
        super(endpointUri, component);
        this.beanName = beanName;
        this.methodName = methodName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MyBatisBeanProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new IllegalArgumentException("Consumer not support on this component (mybatis-bean), use mybatis instead.");
    }

    public String getBeanName() {
        return beanName;
    }

    /**
     * Name of the bean with the MyBatis annotations.
     * This can either by a type alias or a FQN class name.
     */
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * Name of the method on the bean that has the SQL query to be executed.
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

}
