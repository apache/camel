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
package org.apache.camel.component.pojo;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;

/**
 * @version $Revision: 519901 $
 */
public class PojoExchange extends DefaultExchange {

    public PojoExchange(CamelContext context) {
        super(context);
    }

    public PojoInvocation getInvocation() {
        return getIn().getBody(PojoInvocation.class);
    }

    public void setInvocation(PojoInvocation invocation) {
        getIn().setBody(invocation);
    }

    @Override
    public Exchange newInstance() {
        return new PojoExchange(getContext());
    }
}
