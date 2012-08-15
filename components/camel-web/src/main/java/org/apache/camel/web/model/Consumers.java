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