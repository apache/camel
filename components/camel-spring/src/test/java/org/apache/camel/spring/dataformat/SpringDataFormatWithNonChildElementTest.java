package org.apache.camel.spring.dataformat;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.spring.issues.StringDataFormatTest;

public class SpringDataFormatWithNonChildElementTest extends StringDataFormatTest {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/dataformat/dataFormatWithNonChildElementTest.xml");
    }    
}
