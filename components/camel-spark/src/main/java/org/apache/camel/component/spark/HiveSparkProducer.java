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

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.hive.HiveContext;

public class HiveSparkProducer extends DefaultProducer {

    public HiveSparkProducer(SparkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        HiveContext hiveContext = resolveHiveContext();
        String sql = exchange.getIn().getBody(String.class);
        DataFrame resultFrame = hiveContext.sql(sql);
        exchange.getIn().setBody(getEndpoint().isCollect() ? resultFrame.collectAsList() : resultFrame.count());
    }

    @Override
    public SparkEndpoint getEndpoint() {
        return (SparkEndpoint) super.getEndpoint();
    }

    // Helpers

    protected HiveContext resolveHiveContext() {
        Set<HiveContext> hiveContexts = getEndpoint().getComponent().getCamelContext().getRegistry().findByType(HiveContext.class);
        if (hiveContexts.size() == 1) {
            return hiveContexts.iterator().next();
        }
        return null;
    }

}