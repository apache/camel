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
package org.apache.camel.component.digitalocean.producer;

import com.myjeeva.digitalocean.pojo.Regions;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;

/**
 * The DigitalOcean producer for Regions API.
 */
public class DigitalOceanRegionsProducer extends DigitalOceanProducer {

    public DigitalOceanRegionsProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    public void process(Exchange exchange) throws Exception {
        Regions regions = getEndpoint().getDigitalOceanClient().getAvailableRegions(configuration.getPage());
        LOG.trace("All Regions : page {} [{}] ", regions.getRegions(), configuration.getPage());
        exchange.getOut().setBody(regions.getRegions());
    }

}
