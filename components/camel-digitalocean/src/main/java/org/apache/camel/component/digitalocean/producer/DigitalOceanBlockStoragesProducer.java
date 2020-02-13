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

import java.util.List;

import com.myjeeva.digitalocean.pojo.Action;
import com.myjeeva.digitalocean.pojo.Actions;
import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.Region;
import com.myjeeva.digitalocean.pojo.Snapshots;
import com.myjeeva.digitalocean.pojo.Volume;
import com.myjeeva.digitalocean.pojo.Volumes;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for BlockStorages API.
 */
public class DigitalOceanBlockStoragesProducer extends DigitalOceanProducer {

    public DigitalOceanBlockStoragesProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        switch (determineOperation(exchange)) {

            case list:
                getVolumes(exchange);
                break;
            case get:
                getVolume(exchange);
                break;
            case listSnapshots:
                getVolumeSnapshots(exchange);
                break;
            case create:
                createVolume(exchange);
                break;
            case delete:
                deleteVolume(exchange);
                break;
            case attach:
                attachVolumeToDroplet(exchange);
                break;
            case detach:
                detachVolumeToDroplet(exchange);
                break;
            case resize:
                resizeVolume(exchange);
                break;
            case listActions:
                getVolumeActions(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void getVolumes(Exchange exchange) throws Exception {
        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);
        if (ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        Volumes volumes = getEndpoint().getDigitalOceanClient().getAvailableVolumes(region);
        LOG.trace("All Volumes for region {} [{}] ", region, volumes.getVolumes());
        exchange.getOut().setBody(volumes.getVolumes());

    }

    private void createVolume(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        Volume volume = new Volume();

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.VOLUME_SIZE_GIGABYTES))) {
            volume.setSize(in.getHeader(DigitalOceanHeaders.VOLUME_SIZE_GIGABYTES, Integer.class));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.VOLUME_SIZE_GIGABYTES + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.NAME))) {
            volume.setName(in.getHeader(DigitalOceanHeaders.NAME, String.class));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.REGION))) {
            volume.setRegion(new Region(in.getHeader(DigitalOceanHeaders.REGION, String.class)));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(DigitalOceanHeaders.DESCRIPTION))) {
            volume.setDescription(in.getHeader(DigitalOceanHeaders.DESCRIPTION, String.class));
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.DESCRIPTION + " must be specified");
        }

        volume = getEndpoint().getDigitalOceanClient().createVolume(volume);
        LOG.trace("Volume created {}", volume);
        exchange.getOut().setBody(volume);
    }

    private void getVolume(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);
        Volume volume = null;
        if (ObjectHelper.isEmpty(volumeId)) {
            String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);
            String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

            if (ObjectHelper.isEmpty(name) && ObjectHelper.isEmpty(region)) {
                throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.NAME + " and " + DigitalOceanHeaders.REGION + " must be specified");
            }

            List<Volume> volumes = getEndpoint().getDigitalOceanClient().getVolumeInfo(name, region).getVolumes();
            if (volumes.size() > 0) {
                volume = volumes.get(1);
            }
        } else {
            volume = getEndpoint().getDigitalOceanClient().getVolumeInfo(volumeId);
        }

        LOG.trace("Volume [{}] ", volume);
        exchange.getOut().setBody(volume);

    }

    private void getVolumeSnapshots(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);
        if (ObjectHelper.isEmpty(volumeId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        Snapshots snapshots = getEndpoint().getDigitalOceanClient().getVolumeSnapshots(volumeId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("All Snapshots for volume {} [{}] ", volumeId, snapshots.getSnapshots());
        exchange.getOut().setBody(snapshots.getSnapshots());
    }

    private void deleteVolume(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);
        Delete delete;
        if (ObjectHelper.isEmpty(volumeId)) {
            String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);
            String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

            if (ObjectHelper.isEmpty(name) && ObjectHelper.isEmpty(region)) {
                throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.NAME + " and " + DigitalOceanHeaders.REGION + " must be specified");
            }

            delete = getEndpoint().getDigitalOceanClient().deleteVolume(name, region);

        } else {
            delete = getEndpoint().getDigitalOceanClient().deleteVolume(volumeId);
        }

        LOG.trace("Delete Volume [{}] ", delete);
        exchange.getOut().setBody(delete);

    }

    private void attachVolumeToDroplet(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);
        String volumeName = exchange.getIn().getHeader(DigitalOceanHeaders.VOLUME_NAME, String.class);
        Integer dropletId = exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ID, Integer.class);
        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

        if (ObjectHelper.isEmpty(dropletId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_ID + " must be specified");
        }

        if (ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        Action action;

        if (ObjectHelper.isNotEmpty(volumeName)) {
            action = getEndpoint().getDigitalOceanClient().attachVolumeByName(dropletId, volumeName, region);
            LOG.trace("Attach Volume {} to Droplet {} [{}] ", volumeName, dropletId, action);
        } else if (ObjectHelper.isNotEmpty(volumeId)) {
            action = getEndpoint().getDigitalOceanClient().attachVolume(dropletId, volumeId, region);
            LOG.trace("Attach Volume {} to Droplet {} [{}] ", volumeId, dropletId, action);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.VOLUME_NAME + " must be specified");
        }

        exchange.getOut().setBody(action);
    }


    private void detachVolumeToDroplet(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);
        String volumeName = exchange.getIn().getHeader(DigitalOceanHeaders.VOLUME_NAME, String.class);
        Integer dropletId = exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_ID, Integer.class);
        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

        if (ObjectHelper.isEmpty(dropletId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.DROPLET_ID + " must be specified");
        }

        if (ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        Action action;

        if (ObjectHelper.isNotEmpty(volumeName)) {
            action = getEndpoint().getDigitalOceanClient().detachVolumeByName(dropletId, volumeName, region);
            LOG.trace("Detach Volume {} to Droplet {} [{}] ", volumeName, dropletId, action);
        } else if (ObjectHelper.isNotEmpty(volumeId)) {
            action = getEndpoint().getDigitalOceanClient().detachVolume(dropletId, volumeId, region);
            LOG.trace("Detach Volume {} to Droplet {} [{}] ", volumeId, dropletId, action);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.VOLUME_NAME + " must be specified");
        }

        exchange.getOut().setBody(action);

    }

    private void resizeVolume(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);

        if (ObjectHelper.isEmpty(volumeId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

        if (ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        Double size = exchange.getIn().getHeader(DigitalOceanHeaders.VOLUME_SIZE_GIGABYTES, Double.class);

        if (ObjectHelper.isEmpty(size)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.VOLUME_SIZE_GIGABYTES + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().resizeVolume(volumeId, region, size);
        LOG.trace("Resize Volume {} [{}] ", volumeId, action);
    }

    private void getVolumeActions(Exchange exchange) throws Exception {
        String volumeId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, String.class);

        if (ObjectHelper.isEmpty(volumeId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        Actions actions = getEndpoint().getDigitalOceanClient().getAvailableVolumeActions(volumeId);
        LOG.trace("Actions for Volume {} [{}] ", volumeId, actions.getActions());
        exchange.getOut().setBody(actions.getActions());
    }

}
