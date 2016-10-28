package org.apache.camel.component.bonita.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UploadFileResponse {
	
	@JsonProperty("filename")
	private String filename;
	@JsonProperty("tempPath")
	private String tempPath;
	@JsonProperty("contentType")
	private String contentType;
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public String getTempPath() {
		return tempPath;
	}
	public void setTempPath(String tempPath) {
		this.tempPath = tempPath;
	}
	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

}
