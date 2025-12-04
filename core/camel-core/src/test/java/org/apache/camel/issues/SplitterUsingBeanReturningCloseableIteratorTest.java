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

package org.apache.camel.issues;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.util.Iterator;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class SplitterUsingBeanReturningCloseableIteratorTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("mySplitter", new MyOtherSplitterBean());
        return jndi;
    }

    public static class MyOtherSplitterBean {
        public Iterator<String> split(Exchange exchange) {
            return MyCloseableIterator.getInstance();
        }
    }

    @Test
    public void testCloseableIterator() {
        CamelExecutionException e = assertThrows(
                CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Hello,World"),
                "Exception should have been thrown");

        assertTrue(MyCloseableIterator.getInstance().isClosed(), "MyCloseableIterator.close() was not invoked");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").split().method("mySplitter").to("log:foo", "mock:result");
            }
        };
    }
}

final class MyCloseableIterator implements Iterator<String>, Closeable {
    private static MyCloseableIterator singleton;
    private boolean closed;

    private MyCloseableIterator() {}

    public static MyCloseableIterator getInstance() {
        if (singleton == null) {
            singleton = new MyCloseableIterator();
        }
        return singleton;
    }

    @Override
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public String next() {
        throw new RuntimeException("will be closed");
    }

    @Override
    public void remove() {}
}
