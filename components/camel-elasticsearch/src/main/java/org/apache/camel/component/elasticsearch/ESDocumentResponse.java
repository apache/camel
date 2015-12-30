package org.apache.camel.component.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown=true)
public class ESDocumentResponse {

	@JsonProperty("_id")
	private String id;
	
	@JsonProperty("_type")
	private String type;
	
	@JsonProperty("_version")
	private String version;
	
	@JsonProperty("_index")
	private String index;
	
	@JsonProperty("created")
	private boolean isCreated;

	@JsonProperty("_source")
	private JsonNode source;
	
	
	public JsonNode getSource() {
		return source;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}

	public String getIndex() {
		return index;
	}

	public boolean isCreated() {
		return isCreated;
	}
	
	
}
