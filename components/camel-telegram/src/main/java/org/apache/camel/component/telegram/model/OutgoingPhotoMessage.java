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
package org.apache.camel.component.telegram.model;

/**
 * An outgoing photo message.
 */
public class OutgoingPhotoMessage extends OutgoingMessage {

    private static final long serialVersionUID = -6730785675407947090L;

    private byte[] photo;

    private String filenameWithExtension;

    private String caption;

    public OutgoingPhotoMessage() {
    }

    public byte[] getPhoto() {
        return photo;
    }

    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }

    public String getFilenameWithExtension() {
        return filenameWithExtension;
    }

    public void setFilenameWithExtension(String filenameWithExtension) {
        this.filenameWithExtension = filenameWithExtension;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingPhotoMessage{");
        sb.append("photo(length)=").append(photo != null ? photo.length : null);
        sb.append(", filenameWithExtension='").append(filenameWithExtension).append('\'');
        sb.append(", caption='").append(caption).append('\'');
        sb.append('}');
        sb.append(' ');
        sb.append(super.toString());
        return sb.toString();
    }
}
