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
package org.apache.camel.web.model;

import org.apache.camel.web.connectors.CamelDataBean;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Consumers {

    @XmlElement(name = "consumer")
    private List<Consumer> consumers = new ArrayList<Consumer>();

    @Override
    public String toString() {
        return "Consumers " + consumers;
    }

    public List<Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }

    public void load(List<CamelDataBean> managedBeans) {
        for(CamelDataBean managedBean : managedBeans) {
            addConsumer(createConsumer(managedBean));
        }
    }

    protected Consumer createConsumer(CamelDataBean bean) {
        Consumer consumer = new Consumer();
        consumer.load(bean);
        return consumer;
    }

    public void addConsumer(Consumer consumer) {
        getConsumers().add(consumer);
    }

}