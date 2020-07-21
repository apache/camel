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

import com.myjeeva.digitalocean.common.ActionType;
import com.myjeeva.digitalocean.pojo.Action;
import com.myjeeva.digitalocean.pojo.Actions;
import com.myjeeva.digitalocean.pojo.Delete;
import com.myjeeva.digitalocean.pojo.Image;
import com.myjeeva.digitalocean.pojo.Images;
import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.component.digitalocean.constants.DigitalOceanImageTypes;
import org.apache.camel.util.ObjectHelper;

/**
 * The DigitalOcean producer for Images API.
 */
public class DigitalOceanImagesProducer extends DigitalOceanProducer {

    public DigitalOceanImagesProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        switch (determineOperation(exchange)) {

            case list:
                getImages(exchange);
                break;
            case ownList:
                getUserImages(exchange);
                break;
            case listActions:
                getImageActions(exchange);
                break;
            case get:
                getImage(exchange);
                break;
            case update:
                updateImage(exchange);
                break;
            case delete:
                deleteImage(exchange);
                break;
            case transfer:
                transferImage(exchange);
                break;
            case convert:
                convertImageToSnapshot(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }


    private void getUserImages(Exchange exchange) throws Exception {
        Images images = getEndpoint().getDigitalOceanClient().getUserImages(configuration.getPage(), configuration.getPerPage());
        LOG.trace("User images : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(), images.getImages());
        exchange.getOut().setBody(images.getImages());
    }


    private void getImages(Exchange exchange) throws Exception {
        DigitalOceanImageTypes type = exchange.getIn().getHeader(DigitalOceanHeaders.TYPE, DigitalOceanImageTypes.class);
        Images images;

        if (ObjectHelper.isNotEmpty(type)) {
            images = getEndpoint().getDigitalOceanClient().getAvailableImages(configuration.getPage(), configuration.getPerPage(), ActionType.valueOf(type.name()));
        } else {
            images = getEndpoint().getDigitalOceanClient().getAvailableImages(configuration.getPage(), configuration.getPerPage());
        }
        LOG.trace("All Images : page {} / {} per page [{}] ", configuration.getPage(), configuration.getPerPage(), images.getImages());
        exchange.getOut().setBody(images.getImages());
    }

    private void getImage(Exchange exchange) throws Exception {

        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);
        String slug = exchange.getIn().getHeader(DigitalOceanHeaders.DROPLET_IMAGE, String.class);
        Image image;

        if (ObjectHelper.isNotEmpty(imageId)) {
            image = getEndpoint().getDigitalOceanClient().getImageInfo(imageId);
        } else if (ObjectHelper.isNotEmpty(slug)) {
            image = getEndpoint().getDigitalOceanClient().getImageInfo(slug);
        } else {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " or " + DigitalOceanHeaders.DROPLET_IMAGE + " must be specified");
        }

        LOG.trace("Image [{}] ", image);
        exchange.getOut().setBody(image);
    }


    private void getImageActions(Exchange exchange) throws Exception {
        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(imageId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        Actions actions = getEndpoint().getDigitalOceanClient().getAvailableImageActions(imageId, configuration.getPage(), configuration.getPerPage());
        LOG.trace("Actions for Image {} : page {} / {} per page [{}] ", imageId, configuration.getPage(), configuration.getPerPage(), actions.getActions());
        exchange.getOut().setBody(actions.getActions());
    }

    private void updateImage(Exchange exchange) throws Exception {
        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(imageId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        String name = exchange.getIn().getHeader(DigitalOceanHeaders.NAME, String.class);

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.NAME + " must be specified");
        }
        Image image = new Image();
        image.setId(imageId);
        image.setName(name);
        image = getEndpoint().getDigitalOceanClient().updateImage(image);
        LOG.trace("Update Image {} [{}] ", imageId, image);
        exchange.getOut().setBody(image);
    }

    private void deleteImage(Exchange exchange) throws Exception {
        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(imageId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        Delete delete = getEndpoint().getDigitalOceanClient().deleteImage(imageId);
        LOG.trace("Delete  Image {} [{}] ", imageId, delete);
        exchange.getOut().setBody(delete);
    }

    private void transferImage(Exchange exchange) throws Exception {
        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(imageId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        String region = exchange.getIn().getHeader(DigitalOceanHeaders.REGION, String.class);

        if (ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.REGION + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().transferImage(imageId, region);
        LOG.trace("Transfer  Image {} to Region {} [{}] ", imageId, region, action);
        exchange.getOut().setBody(action);
    }

    private void convertImageToSnapshot(Exchange exchange) throws Exception {
        Integer imageId = exchange.getIn().getHeader(DigitalOceanHeaders.ID, Integer.class);

        if (ObjectHelper.isEmpty(imageId)) {
            throw new IllegalArgumentException(DigitalOceanHeaders.ID + " must be specified");
        }

        Action action = getEndpoint().getDigitalOceanClient().convertImage(imageId);
        LOG.trace("Convert Image {} [{}] ", imageId, action);
        exchange.getOut().setBody(action);
    }
}
