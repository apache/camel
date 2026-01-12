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
package org.apache.camel.component.telegram.model;

import java.io.Serial;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The paid media is a photo.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#paidmediaphoto">https://core.telegram.org/bots/api#paidmediaphoto</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaidMediaPhoto extends PaidMedia {

    @Serial
    private static final long serialVersionUID = 1838194783381263973L;

    /**
     * The photo.
     */
    private List<IncomingPhotoSize> photo;

    public PaidMediaPhoto() {
        super("photo");
    }

    public List<IncomingPhotoSize> getPhoto() {
        return photo;
    }

    public void setPhoto(List<IncomingPhotoSize> photo) {
        this.photo = photo;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaidMediaPhoto{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", photo=").append(photo);
        sb.append('}');
        return sb.toString();
    }
}
