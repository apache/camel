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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.myjeeva.digitalocean.common.ResourceType;
import com.myjeeva.digitalocean.pojo.Action;
import com.myjeeva.digitalocean.pojo.Actions;
import com.myjeeva.digitalocean.pojo.Backups;
import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.Droplet;
import com.myjeeva.digitalocean.pojo.Droplets;
import com.myjeeva.digitalocean.pojo.Image;
import com.myjeeva.digitalocean.pojo.Kernels;
import com.myjeeva.digitalocean.pojo.Key;
import com.myjeeva.digitalocean.pojo.Neighbors;
import com.myjeeva.digitalocean.pojo.Region;
import com.myjeeva.digitalocean.pojo.Resource;
import com.myjeeva.digitalocean.pojo.Response;
import com.myjeeva.digitalocean.pojo.Snapshots;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.component.digitalocean.constants.DigitalOceanOperations;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for Droplets API.
 */
public class DigitalOceanDropletsProducer extends DigitalOceanProducer {

    private Integer dropletId;

    public DigitalOceanDropletsProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    public void process(Exchange exchange) throws Exception {

        DigitalOceanOperations op = determineOperation(exchange);
        if (op != DigitalOceanOperations.create && op != DigitalOceanOperations.list && op != DigitalOceanOperations.listAllNeighbors) {
            dropletId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);
            if (ObjectHelper.isEmpty(dropletId)) {
                throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
            }

        }

        switch (op) {
        case create:
            createDroplet(exchange);
            break;
        case list:
            getDroplets(exchange);
            break;
        case delete:
            deleteDroplet(exchange);
            break;
        case get:
            getDroplet(exchange);
            break;
        case listActions:
            getDropletActions(exchange);
            break;
        case listSnapshots:
            getDropletSnapshots(exchange);
            break;
        case listKernels:
            getDropletKernels(exchange);
            break;
        case listBackups:
            getDropletBackups(exchange);
            break;
        case listNeighbors:
            getDropletNeighbors(exchange);
            break;
        case listAllNeighbors:
            getAllDropletNeighbors(exchange);
            break;
        case enableBackups:
            enableDropletBackups(exchange);
            break;
        case disableBackups:
            disableDropletBackups(exchange);
            break;
        case reboot:
            rebootDroplet(exchange);
            break;
        case powerCycle:
            powerCycleDroplet(exchange);
            break;
        case shutdown:
            shutdownDroplet(exchange);
            break;
        case powerOn:
            powerOnDroplet(exchange);
            break;
        case powerOff:
            powerOffDroplet(exchange);
            break;
        case restore:
            restoreDroplet(exchange);
            break;
        case resetPassword:
            resetDropletPassword(exchange);
            break;
        case resize:
            resizeDroplet(exchange);
            break;
        case rebuild:
            rebuildDroplet(exchange);
            break;
        case rename:
            renameDroplet(exchange);
            break;
        case changeKernel:
            changeDropletKernel(exchange);
            break;
        case enableIpv6:
            enableDropletIpv6(exchange);
            break;
        case enablePrivateNetworking:
            enableDropletPrivateNetworking(exchange);
            break;
        case takeSnapshot:
            takeDropletSnapshot(exchange);
            break;
        case tag:
            tagDroplet(exchange);
            break;
        case untag:
            untagDroplet(exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }


    private void getDroplet(Exchange exchange) throws Exception {
        Droplet droplet = getEndpoint().getDigitalOceanClient().getDropletInfo(dropletId);
        LOG.trace("Droplet {} ", droplet);
        exchange.getOut().setBody(droplet);
    }


    private void getDroplets(Exchange exchange) throws Exception {
        Droplets droplets = getEndpoint().getDigitalOceanClient().getAvailableDroplets(configuration.getPage(), configuration.getPerPage());
        LOG.trace("All Droplets : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(), droplets.getDroplets());
        exchange.getOut().setBody(droplets.getDroplets());
    }


    private void getDropletActions(Exchange exchange) throws Exception {
        Actions actions = getEndpoint().getDigitalOceanClient().getAvailableDropletActions(dropletId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Actions for Droplet {} : page {} / {} per page [{}] ", dropletId, configuration.getPage(), configuration.getPerPage(), actions.getActions());
        exchange.getOut().setBody(actions.getActions());
    }

    private void getDropletKernels(Exchange exchange) throws Exception {
        Kernels kernels = getEndpoint().getDigitalOceanClient().getDropletKernels(dropletId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Kernels for Droplet {} : page {} / {} per page [{}] ", dropletId, configuration.getPage(), configuration.getPerPage(), kernels.getKernels());
        exchange.getOut().setBody(kernels.getKernels());
    }

    private void getDropletBackups(Exchange exchange) throws Exception {
        Backups backups = getEndpoint().getDigitalOceanClient().getDropletBackups(dropletId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Backups for Droplet {} : page {} / {} per page [{}] ", dropletId, configuration.getPage(), configuration.getPerPage(), backups.getBackups());
        exchange.getOut().setBody(backups.getBackups());
    }

    private void getDropletSnapshots(Exchange exchange) throws Exception {
        Snapshots snapshots = getEndpoint().getDigitalOceanClient().getDropletSnapshots(dropletId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Snapshots for Droplet {} : page {} / {} per page [{}] ", dropletId, configuration.getPage(), configuration.getPerPage(), snapshots.getSnapshots());
        exchange.getOut().setBody(snapshots.getSnapshots());
    }

    private void getDropletNeighbors(Exchange exchange) throws Exception {
        Droplets droplets = getEndpoint().getDigitalOceanClient().getDropletNeighbors(dropletId, configuration.getPage());
        LOG.trace("Neighbors for Droplet {} : page {} [{}] ", dropletId, configuration.getPage(), droplets.getDroplets());
        exchange.getOut().setBody(droplets.getDroplets());
    }

    private void getAllDropletNeighbors(Exchange exchange) throws Exception {
        Neighbors neighbors = getEndpoint().getDigitalOceanClient().getAllDropletNeighbors(configuration.getPage());
        LOG.trace("All Neighbors : page {} [{}] ", configuration.getPage(), neighbors.getNeighbors());
        exchange.getOut().setBody(neighbors.getNeighbors());
    }

    private void deleteDroplet(Exchange exchange) throws Exception {
        Delete delete = getEndpoint().getDigitalOceanClient().deleteDroplet(dropletId);
        LOG.trace("Delete Droplet {} ", delete);
        exchange.getOut().setBody(delete);
    }

    @SuppressWarnings("unchecked")
    private void createDroplet(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        Droplet droplet = new Droplet();

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.REGION))) {
            droplet.setRegion(new Region(in.getHeader(DigitalOceanHeaders.REGION, String.class)));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_SIZE))) {
            droplet.setSize(in.getHeader(DigitalOceanHeaders.DROPLET_SIZE, String.class));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_SIZE + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_IMAGE))) {
            droplet.setImage(new Image(in.getHeader(DigitalOceanHeaders.DROPLET_IMAGE, String.class)));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_IMAGE + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_KEYS))) {
            List<String> keys = (List<String>) exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_KEYS);
            droplet.setKeys(keys.stream().map(Key::new).collect(Collectors.toList()));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ENABLE_BACKUPS))) {
            droplet.setEnableBackup(in.getHeader(DigitalOceanHeaders.DROPLET_ENABLE_BACKUPS, Boolean.class));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ENABLE_IPV6))) {
            droplet.setEnableIpv6(in.getHeader(DigitalOceanHeaders.DROPLET_ENABLE_IPV6, Boolean.class));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ENABLE_PRIVATE_NETWORKING))) {
            droplet.setEnablePrivateNetworking(in.getHeader(DigitalOceanHeaders.DROPLET_ENABLE_PRIVATE_NETWORKING, Boolean.class));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_USER_DATA))) {
            droplet.setUserData(in.getHeader(DigitalOceanHeaders.DROPLET_USER_DATA, String.class));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_VOLUMES))) {
            droplet.setVolumeIds((List<String>) exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_VOLUMES));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_TAGS))) {
            droplet.setTags((List<String>) exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_TAGS));
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAMES))) {
            droplet.setNames((List<String>) in.getHeader(DigitalOceanHeaders.NAMES));
            Droplets droplets = getEndpoint().getDigitalOceanClient().createDroplets(droplet);
            LOG.trace("Droplets created {} ", droplets);
            exchange.getOut().setBody(droplets.getDroplets());
        } else if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            droplet.setName(in.getHeader(DigitalOceanHeaders.NAME, String.class));
            droplet = getEndpoint().getDigitalOceanClient().createDroplet(droplet);
            LOG.trace("Droplet created {} ", droplet);
            exchange.getOut().setBody(droplet);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAMES + " or " + DigitalOceanHeaders.NAME + " must be specified");
        }

    }

    private void restoreDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.IMAGE_ID))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.IMAGE_ID + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().restoreDroplet(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.IMAGE_ID, Integer.class));
        LOG.trace("DropletAction Restore [{}] ", action);
        exchange.getOut().setBody(action);

    }

    private void resizeDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_SIZE))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_SIZE + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().resizeDroplet(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_SIZE, String.class));
        LOG.trace("DropletAction Resize [{}] ", action);
        exchange.getOut().setBody(action);
    }


    private void rebuildDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.IMAGE_ID))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.IMAGE_ID + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().rebuildDroplet(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.IMAGE_ID, Integer.class));
        LOG.trace("Rebuild Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void renameDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().renameDroplet(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class));
        LOG.trace("Rename Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }


    private void changeDropletKernel(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.KERNEL_ID))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.KERNEL_ID + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().changeDropletKernel(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.KERNEL_ID, Integer.class));
        LOG.trace("Rename Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void resetDropletPassword(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().resetDropletPassword(dropletId);
        LOG.trace("Reset password Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }


    private void powerOnDroplet(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().powerOnDroplet(dropletId);
        LOG.trace("Power on Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void powerOffDroplet(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().powerOffDroplet(dropletId);
        LOG.trace("Power off Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void shutdownDroplet(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().shutdownDroplet(dropletId);
        LOG.trace("Shutdown Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void powerCycleDroplet(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().powerCycleDroplet(dropletId);
        LOG.trace("Power cycle Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void enableDropletBackups(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().enableDropletBackups(dropletId);
        LOG.trace("Enable backups Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void disableDropletBackups(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().disableDropletBackups(dropletId);
        LOG.trace("Disable backups for Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void enableDropletIpv6(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().enableDropletIpv6(dropletId);
        LOG.trace("Enable IP v6 for Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void enableDropletPrivateNetworking(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().enableDropletPrivateNetworking(dropletId);
        LOG.trace("Enable private networking for Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }


    private void rebootDroplet(Exchange exchange) throws Exception {
        Action action = getEndpoint().getDigitalOceanClient().rebootDroplet(dropletId);
        LOG.trace("Reboot Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void takeDropletSnapshot(Exchange exchange) throws Exception {
        Action action;

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            action = getEndpoint().getDigitalOceanClient().takeDropletSnapshot(dropletId, exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class));
        } else {
            action = getEndpoint().getDigitalOceanClient().takeDropletSnapshot(dropletId);
        }

        LOG.trace("Take Snapshot for Droplet {} : [{}] ", dropletId, action);
        exchange.getOut().setBody(action);
    }

    private void tagDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }

        ArrayList<Resource> resources = new ArrayList<>(1);
        resources.add(new Resource(dropletId.toString(), ResourceType.DROPLET));
        Response response = getEndpoint().getDigitalOceanClient().tagResources(dropletId.toString(), resources);
        LOG.trace("Tag Droplet {} : [{}] ", dropletId, response);
        exchange.getOut().setBody(response);
    }

    private void untagDroplet(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }

        ArrayList<Resource> resources = new ArrayList<>(1);
        resources.add(new Resource(dropletId.toString(), ResourceType.DROPLET));
        Response response = getEndpoint().getDigitalOceanClient().untagResources(dropletId.toString(), resources);
        LOG.trace("Untag Droplet {} : [{}] ", dropletId, response);
        exchange.getOut().setBody(response);
    }

}
