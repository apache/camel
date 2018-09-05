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
package org.apache.camel.component.twitter.deprecated;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.twitter.CamelTwitterTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

import twitter4j.Status;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;

@Deprecated
public class SearchEventTest extends CamelTwitterTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    private TwitterStream twitterStream;
    private StatusListener listener;

    @Test
    public void testSearchTimeline() throws Exception {
        resultEndpoint.expectedMinimumMessageCount(1);
        Status status = (Status) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{Status.class},
                new TwitterHandler());

        listener.onStatus(status);
        //"#cameltest tweet");
        resultEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("twitter://streaming/filter?type=event&twitterStream=#twitterStream&keywords=#cameltest")
                        .transform(body().convertToString()).to("mock:result");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        twitterStream = (TwitterStream) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{TwitterStream.class},
                new TwitterHandler());
        JndiRegistry registry = super.createRegistry();
        registry.bind("twitterStream", twitterStream);
        return registry;
    }

    public class TwitterHandler implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //mock some methods
            if ("addListener".equals(method.getName())) {
                listener = (StatusListener) args[0];
            } else if ("toString".equals(method.getName())) {
                return this.toString();
            } else if ("getText".equals(method.getName())) {
                return "#cameltest tweet";
            } else if ("getUser".equals(method.getName())) {
                return Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class[]{twitter4j.User.class},
                        new TwitterHandler());
            }
            return null;
        }
    }
}
