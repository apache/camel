/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.rx;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import rx.Observable;

public class LoginServiceObservableTest extends CamelTestSupport {

    @Test
    public void testBeanObservable() throws Exception {
        ReactiveCamel reactiveCamel = new ReactiveCamel(context);

        // consume from two endpoints and aggregate by appending the data
        Observable<String> login = reactiveCamel.toObservable("seda:login", String.class);
        Observable<String> user = reactiveCamel.toObservable("seda:user", String.class);
        Observable<String> result = Observable.combineLatest(login, user, (a, b) -> a + "=" + b);

        getMockEndpoint("mock:result").expectedBodiesReceived("OK=Donald Duck");

        // send in data
        template.sendBody("seda:login", "OK");
        template.sendBody("seda:user", "Donald Duck");

        // and send the results to the mock endpoint
        reactiveCamel.sendTo(result, "mock:result");

        assertMockEndpointsSatisfied();
    }

}