package org.apache.camel.component.yammer;

import java.io.InputStream;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;

public abstract class YammerComponentTestSupport extends CamelTestSupport {

    protected YammerComponent yammerComponent;

    public YammerComponentTestSupport() {
        super();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    
        yammerComponent = context.getComponent("yammer", YammerComponent.class);
        YammerConfiguration config = yammerComponent.getConfig();
        InputStream is = getClass().getResourceAsStream(jsonFile());
        String messages = context.getTypeConverter().convertTo(String.class, is);
        config.setRequestor(new TestApiRequestor(messages));
    }

    protected String jsonFile() {
        return "/messages.json";
    }

}