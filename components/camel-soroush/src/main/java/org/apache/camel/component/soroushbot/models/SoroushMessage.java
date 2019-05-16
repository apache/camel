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
package org.apache.camel.component.soroushbot.models;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.camel.component.soroushbot.component.SoroushBotEndpoint;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoroushMessage implements Cloneable {
    private String to;
    private String from;
    private String body;
    private MinorType type;
    private String time;
    private String fileName;
    private FileType fileType;
    private Double fileSize;
    private String fileUrl;
    private String thumbnailUrl;
    private Double imageWidth;
    private Double imageHeight;
    private Double fileDuration;
    private Double thumbnailWidth;
    private Double thumbnailHeight;
    private String nickName;
    private String avatarUrl;
    private Double phoneNo;
    private Double latitude;
    private Double longitude;
    private List<List<CustomKey>> keyboard;
    @JsonIgnore
    private InputStream file;
    @JsonIgnore
    private InputStream thumbnail;

    public SoroushMessage() {
    }

    public SoroushMessage(String to, String from, String body, MinorType type, String time, String fileName, FileType fileType, Double fileSize, String fileUrl, String thumbnailUrl,
                          Double imageWidth, Double imageHeight, Double fileDuration, Double thumbnailWidth, Double thumbnailHeight, String nickName, String avatarUrl, Double phoneNo,
                          Double latitude, Double longitude, List<List<CustomKey>> keyboard) {
        this.to = to;
        this.from = from;
        this.body = body;
        this.type = type;
        this.time = time;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.fileDuration = fileDuration;
        this.thumbnailWidth = thumbnailWidth;
        this.thumbnailHeight = thumbnailHeight;
        this.nickName = nickName;
        this.avatarUrl = avatarUrl;
        this.phoneNo = phoneNo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.keyboard = keyboard;
    }

    /**
     * set uploading file to a file
     * this field help you store file in the message, it also let you automatically upload
     * it to the soroush server by sending this message to {@code uploadFile} or {@code sendMessage} endpoint.
     * auto upload file is working in the following condition:
     * for {@code sendMessage} endpoint:
     * {@link SoroushMessage#file}{@code !=null && }{@link SoroushBotEndpoint#autoUploadFile} {@code  && (}
     * {@link SoroushMessage#fileUrl}{@code ==null || }{@link SoroushBotEndpoint#forceUpload}{@code )}
     * for {@code uploadFile} endpoint:
     * {@link SoroushMessage#file}{@code !=null && (}
     * {@link SoroushMessage#fileUrl}{@code ==null || }{@link SoroushBotEndpoint#forceUpload}{@code )}
     *
     * @param file to be uploaded
     * @throws FileNotFoundException if file not found
     * @see SoroushBotEndpoint#forceUpload
     * @see SoroushBotEndpoint#autoUploadFile
     */
    @JsonIgnore
    public void setFile(File file) throws FileNotFoundException {
        this.file = new BufferedInputStream(new FileInputStream(file));
    }

    /**
     * set uploading thumbnail to a {@code thumbnail}
     * this field help you store thumbnail in the message, it also let you automatically upload
     * it to the soroush server by sending this message to {@code uploadFile} or {@code sendMessage} endpoint.
     * auto upload thumbnail is working in the following condition:
     * for {@code sendMessage} endpoint:
     * {@link SoroushMessage#thumbnail}{@code !=null && }{@link SoroushBotEndpoint#autoUploadFile} {@code  && (}
     * {@link SoroushMessage#thumbnailUrl}{@code ==null || }{@link SoroushBotEndpoint#forceUpload}{@code )}
     * for {@code uploadFile} endpoint:
     * {@link SoroushMessage#thumbnail}{@code !=null && (}
     * {@link SoroushMessage#thumbnailUrl}{@code ==null || }{@link SoroushBotEndpoint#forceUpload}{@code )}
     *
     * @param thumbnail to be uploaded
     * @throws FileNotFoundException if file not found
     * @see SoroushBotEndpoint#forceUpload
     * @see SoroushBotEndpoint#autoUploadFile
     */
    @JsonIgnore
    public void setThumbnail(File thumbnail) throws FileNotFoundException {
        this.thumbnail = new BufferedInputStream(new FileInputStream(thumbnail));
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public MinorType getType() {
        return type;
    }

    public void setType(MinorType type) {
        this.type = type;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public Double getFileSize() {
        return fileSize;
    }

    public void setFileSize(Double fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Double getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Double imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Double getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Double imageHeight) {
        this.imageHeight = imageHeight;
    }

    public Double getFileDuration() {
        return fileDuration;
    }

    public void setFileDuration(Double fileDuration) {
        this.fileDuration = fileDuration;
    }

    public Double getThumbnailWidth() {
        return thumbnailWidth;
    }

    public void setThumbnailWidth(Double thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
    }

    public Double getThumbnailHeight() {
        return thumbnailHeight;
    }

    public void setThumbnailHeight(Double thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Double getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(Double phoneNo) {
        this.phoneNo = phoneNo;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public List<List<CustomKey>> getKeyboard() {
        return keyboard;
    }

    public void setKeyboard(List<List<CustomKey>> keyboard) {
        this.keyboard = keyboard;
    }

    public InputStream getFile() {
        return file;
    }

    public void setFile(InputStream file) {
        this.file = file;
    }

    public InputStream getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(InputStream thumbnail) {
        this.thumbnail = thumbnail;
    }

    @Override
    public String toString() {
        return "SoroushMessage{"
                + "to='" + to + '\''
                + ", from='" + from + '\''
                + ", body='" + body + '\''
                + ", type=" + type
                + ", time='" + time + '\''
                + ", fileName='" + fileName + '\''
                + ", fileType=" + fileType
                + ", fileSize=" + fileSize
                + ", fileUrl='" + fileUrl + '\''
                + ", thumbnailUrl='" + thumbnailUrl + '\''
                + ", imageWidth=" + imageWidth
                + ", imageHeight=" + imageHeight
                + ", fileDuration=" + fileDuration
                + ", thumbnailWidth=" + thumbnailWidth
                + ", thumbnailHeight=" + thumbnailHeight
                + ", nickName='" + nickName + '\''
                + ", avatarUrl='" + avatarUrl + '\''
                + ", phoneNo=" + phoneNo
                + ", latitude=" + latitude
                + ", longitude=" + longitude
                + ", keyboard=" + keyboard
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SoroushMessage)) {
            return false;
        }
        SoroushMessage that = (SoroushMessage) o;
        return Objects.equals(getTo(), that.getTo())
                && Objects.equals(getFrom(), that.getFrom())
                && Objects.equals(getBody(), that.getBody())
                && getType() == that.getType()
                && Objects.equals(getTime(), that.getTime())
                && Objects.equals(getFileName(), that.getFileName())
                && getFileType() == that.getFileType()
                && Objects.equals(getFileSize(), that.getFileSize())
                && Objects.equals(getFileUrl(), that.getFileUrl())
                && Objects.equals(getThumbnailUrl(), that.getThumbnailUrl())
                && Objects.equals(getImageWidth(), that.getImageWidth())
                && Objects.equals(getImageHeight(), that.getImageHeight())
                && Objects.equals(getFileDuration(), that.getFileDuration())
                && Objects.equals(getThumbnailWidth(), that.getThumbnailWidth())
                && Objects.equals(getThumbnailHeight(), that.getThumbnailHeight())
                && Objects.equals(getNickName(), that.getNickName())
                && Objects.equals(getAvatarUrl(), that.getAvatarUrl())
                && Objects.equals(getPhoneNo(), that.getPhoneNo())
                && Objects.equals(getLatitude(), that.getLatitude())
                && Objects.equals(getLongitude(), that.getLongitude())
                && Objects.equals(getKeyboard(), that.getKeyboard());
    }

    @Override
    public SoroushMessage clone() throws CloneNotSupportedException {
        return new SoroushMessage(to,
                from,
                body,
                type,
                time,
                fileName,
                fileType,
                fileSize,
                fileUrl,
                thumbnailUrl,
                imageWidth,
                imageHeight,
                fileDuration,
                thumbnailWidth,
                thumbnailHeight,
                nickName,
                avatarUrl,
                phoneNo,
                latitude,
                longitude,
                (keyboard == null) ? null : keyboard.stream().map(it -> it == null ? null : new ArrayList<>(it)).collect(Collectors.toList())
        );
    }

    @Override
    public int hashCode() {

        return Objects.hash(getTo(), getFrom(), getBody(), getType(), getTime(), getFileName(), getFileType(),
                getFileSize(), getFileUrl(), getThumbnailUrl(), getImageWidth(), getImageHeight(), getFileDuration(),
                getThumbnailWidth(), getThumbnailHeight(), getNickName(), getAvatarUrl(), getPhoneNo(), getLatitude(),
                getLongitude(), getKeyboard());
    }
}
