package org.apache.camel.itest.osgi.hazelcast;

import java.io.Serializable;

public class SimpleObject implements Serializable {

    Long id;
    Object value;

    public SimpleObject(Long id) {
        this.id = id;
    }

    public SimpleObject(Long id, Object value) {
        this.id = id;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleObject that = (SimpleObject) o;

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
