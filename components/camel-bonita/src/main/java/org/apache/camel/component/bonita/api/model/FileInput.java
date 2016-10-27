package org.apache.camel.component.bonita.api.model;

import java.io.Serializable;

public class FileInput implements Serializable {
	
	private String filename;
	private byte[] content;
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public byte[] getContent() {
		return content;
	}
	public void setContent(byte[] content) {
		this.content = content;
	}
	public FileInput(String filename, byte[] content) {
		super();
		this.filename = filename;
		this.content = content;
	} 
	
	

}
