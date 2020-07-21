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
package org.apache.camel.component.digitalocean.producer;

import com.myjeeva.digitalocean.pojo.Action;
import com.myjeeva.digitalocean.pojo.Actions;
import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.FloatingIP;
import com.myjeeva.digitalocean.pojo.FloatingIPs;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for FloatingIps API.
 */
public class DigitalOceanFloatingIPsProducer extends DigitalOceanProducer {

    public DigitalOceanFloatingIPsProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {

            case list:
                getFloatingIPs(exchange);
                break;
            case create:
                createFloatingIp(exchange);
                break;
            case get:
                getFloatingIP(exchange);
                break;
            case delete:
                deleteFloatingIP(exchange);
                break;
            case assign:
                assignFloatingIPToDroplet(exchange);
                break;
            case unassign:
                unassignFloatingIP(exchange);
                break;
            case listActions:
                getFloatingIPActions(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void createFloatingIp(Exchange exchange) throws Exception {
        Integer dropletId = exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ID, Integer.class);
        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);
        FloatingIP ip;

        if (ObjectHelper.isNotEmpty(dropletId)) {
            ip = getEndpoint().getDigitalOceanClient().createFloatingIP(dropletId);
        } else if (ObjectHelper.isNotEmpty(region)) {
            ip = getEndpoint().getDigitalOceanClient().createFloatingIP(region);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_ID + " or " + DigitalOceanHeaders.REGION + " must be specified");
        }

        LOG.trace("FloatingIP [{}] ", ip);
        exchange.getOut().setBody(ip);
    }


    private void getFloatingIPs(Exchange exchange) throws Exception {
        FloatingIPs ips = getEndpoint().getDigitalOceanClient().getAvailableFloatingIPs(configuration.getPage(), configuration.getPerPage());
        LOG.trace("All Floating IPs : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(), ips.getFloatingIPs());
        exchange.getOut().setBody(ips.getFloatingIPs());
    }


    private void getFloatingIP(Exchange exchange) throws Exception {
        String ipAddress = exchange.getIn().getHeader(DigitalOceanHeaders.FLOATING_IP_ADDRESS, String.class);

        if (ObjectHelper.isEmpty(ipAddress)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.FLOATING_IP_ADDRESS + " must be specified");
        }


        FloatingIP ip = getEndpoint().getDigitalOceanClient().getFloatingIPInfo(ipAddress);
        LOG.trace("Floating IP {}", ip);
        exchange.getOut().setBody(ip);
    }


    private void deleteFloatingIP(Exchange exchange) throws Exception {
        String ipAddress = exchange.getIn().getHeader(DigitalOceanHeaders.FLOATING_IP_ADDRESS, String.class);

        if (ObjectHelper.isEmpty(ipAddress)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.FLOATING_IP_ADDRESS + " must be specified");
        }


        Delete delete = getEndpoint().getDigitalOceanClient().deleteFloatingIP(ipAddress);
        LOG.trace("Delete Floating IP {}", delete);
        exchange.getOut().setBody(delete);
    }

    private void assignFloatingIPToDroplet(Exchange exchange) throws Exception {
        Integer dropletId = exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ID, Integer.class);

        if (ObjectHelper.isEmpty(dropletId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_ID + " must be specified");
        }

        String ipAddress = exchange.getIn().getHeader(DigitalOceanHeaders.FLOATING_IP_ADDRESS, String.class);

        if (ObjectHelper.isEmpty(ipAddress)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.FLOATING_IP_ADDRESS + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().assignFloatingIP(dropletId, ipAddress);
        LOG.trace("Assign Floating IP to Droplet {}", action);
        exchange.getOut().setBody(action);
    }

    private void unassignFloatingIP(Exchange exchange) throws Exception {
        String ipAddress = exchange.getIn().getHeader(DigitalOceanHeaders.FLOATING_IP_ADDRESS, String.class);

        if (ObjectHelper.isEmpty(ipAddress)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.FLOATING_IP_ADDRESS + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().unassignFloatingIP(ipAddress);
        LOG.trace("Unassign Floating IP {}", action);
        exchange.getOut().setBody(action);
    }

    private void getFloatingIPActions(Exchange exchange) throws Exception {
        String ipAddress = exchange.getIn().getHeader(DigitalOceanHeaders.FLOATING_IP_ADDRESS, String.class);

        if (ObjectHelper.isEmpty(ipAddress)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.FLOATING_IP_ADDRESS + " must be specified");
        }

        Actions actions = getEndpoint().getDigitalOceanClient().getAvailableFloatingIPActions(ipAddress, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Actions for FloatingIP {} : page {} / {} per page [{}] ", ipAddress, configuration.getPage(), configuration.getPerPage(), actions.getActions());
        exchange.getOut().setBody(actions.getActions());
    }

}
