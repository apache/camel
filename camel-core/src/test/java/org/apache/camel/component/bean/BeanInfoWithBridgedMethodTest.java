package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultExchange;

/**
 * Unit test for bridged methods.
 */
public class BeanInfoWithBridgedMethodTest extends ContextTestSupport {

    public void testBridgedMethod() throws Exception {
        BeanInfo beanInfo = new BeanInfo(context, MyService.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request(1));

        try {
            MyService myService = new MyService();
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertEquals("MyService", mi.getMethod().getDeclaringClass().getSimpleName());
            assertEquals(2, mi.getMethod().invoke(myService, new Request(1)));
        } catch (AmbiguousMethodCallException e) {
            fail("This should not be ambiguous!");
        }
    }

    public void testPackagePrivate() throws Exception {
        BeanInfo beanInfo = new BeanInfo(context, MyPackagePrivateService.class);

        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new Request(1));

        try {
            MyPackagePrivateService myService = new MyPackagePrivateService();
            MethodInvocation mi = beanInfo.createInvocation(null, exchange);
            assertEquals("Service", mi.getMethod().getDeclaringClass().getSimpleName());
            assertEquals(4, mi.getMethod().invoke(myService, new Request(2)));
        } catch (AmbiguousMethodCallException e) {
            fail("This should not be ambiguous!");
        }
    }

    public static class Request {
        int x;

        public Request(int x) {
            this.x = x;
        }
    }

    public interface Service<R> {

        int process(R request);
    }

    public static class MyService implements Service<Request> {

        public int process(Request request) {
            return request.x + 1;
        }
    }

    static class MyPackagePrivateService implements Service<Request> {

        public int process(Request request) {
            return request.x + 2;
        }
    }

}
