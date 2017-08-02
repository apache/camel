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
package org.apache.camel.component.servicenow;

import org.apache.camel.component.servicenow.releases.fuji.FujiServiceNowProducer;
import org.apache.camel.component.servicenow.releases.helsinki.HelsinkiServiceNowProducer;

public enum ServiceNowRelease implements ServiceNowProducerSupplier {
    FUJI {
        @Override
        public ServiceNowProducer get(ServiceNowEndpoint endpoint) throws Exception {
            return new FujiServiceNowProducer(endpoint);
        }
    },
    GENEVA {
        @Override
        public ServiceNowProducer get(ServiceNowEndpoint endpoint) throws Exception {
            return null;
        }
    },
    HELSINKI {
        @Override
        public ServiceNowProducer get(ServiceNowEndpoint endpoint) throws Exception {
            return new HelsinkiServiceNowProducer(endpoint);
        }
    }
}
