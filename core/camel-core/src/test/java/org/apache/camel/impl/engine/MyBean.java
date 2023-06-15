package org.apache.camel.impl.engine;

@org.apache.camel.spi.annotations.Component(value = "MyBean")
public class MyBean {
    public String addString(String source, String dst) throws Exception {
        return source + dst;
    }
}
