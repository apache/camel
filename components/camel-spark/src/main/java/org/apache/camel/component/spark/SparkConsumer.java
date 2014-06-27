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
package org.apache.camel.component.spark;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import spark.Spark;

public class SparkConsumer extends DefaultConsumer {

    private final CamelSparkRoute route;

    public SparkConsumer(Endpoint endpoint, Processor processor, CamelSparkRoute route) {
        super(endpoint, processor);
        this.route = route;
    }

    @Override
    public SparkEndpoint getEndpoint() {
        return (SparkEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String path = getEndpoint().getPath();
        String verb = getEndpoint().getVerb();
        String accept = getEndpoint().getAccept();

        // TODO: reuse our spark route builder DSL instead of this code

        if ("get".equals(verb)) {
            log.info("get(/{})", verb);
            if (accept != null) {
                Spark.get(path, accept, route);
            } else {
                Spark.get(path, route);
            }
        }
    }

}
