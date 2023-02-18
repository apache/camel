package org.apache.camel.component.bean;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class BeanProcessorOverloadedMethodsWithBracketsTest extends CamelTestSupport {

    private final String strArgWithBrackets = ")(string_with_brackets()))())";

    @Test
    public void testOverloadedMethodWithBracketsParams() throws InterruptedException {
        template.sendBody("direct:start", null);
        MockEndpoint mock = getMockEndpoint("mock:result");
        String receivedExchangeBody = mock.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(new MyOverloadedClass().myMethod(strArgWithBrackets, strArgWithBrackets), receivedExchangeBody);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .bean(MyOverloadedClass.class, "myMethod('" + strArgWithBrackets + "', '" + strArgWithBrackets + "')")
                        .to("mock:result");
            }
        };
    }

    public static class MyOverloadedClass {
        public String myMethod() {
            return "";
        }

        public String myMethod(String str) {
            return str;
        }

        public String myMethod(String str1, String str2) {
            return str1 + str2;
        }
    }
}
