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
package org.apache.camel.test.spring;

import java.util.EventObject;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.support.EventNotifierSupport;

public class RouteCoverageEventNotifier extends EventNotifierSupport {

    private final String testClassName;
    private final Function testMethodName;

    public RouteCoverageEventNotifier(String testClassName, Function testMethodName) {
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        setIgnoreCamelContextEvents(false);
        setIgnoreExchangeEvents(true);
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof CamelContextStoppingEvent;
    }

    @Override
    public void notify(EventObject event) throws Exception {
        CamelContext context = ((CamelContextStoppingEvent) event).getContext();
        String testName = (String) testMethodName.apply(this);
        RouteCoverageDumper.dumpRouteCoverage(context, testClassName, testName);
    }

}
