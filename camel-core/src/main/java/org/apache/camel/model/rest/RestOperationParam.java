package org.apache.camel.model.rest;

import org.apache.camel.spi.Metadata;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by seb on 5/13/15.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "param")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestOperationParam {
    @XmlAttribute
    String paramType="query";

    @XmlAttribute
    String name;

    @XmlAttribute
    String description="";

    @XmlAttribute
    String defaultValue="";

    @XmlAttribute
    Boolean required=true;

    @XmlAttribute
    Boolean allowMultiple=false;

    @XmlAttribute
    String dataType="string";

    @XmlElementWrapper( name="allowableValues" )
    @XmlElement( name="value" )
    List<String> allowableValues=new ArrayList<String>();

    @XmlAttribute
    String paramAccess=null;


    public RestOperationParam() {
    
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getAllowMultiple() {
        return allowMultiple;
    }

    public void setAllowMultiple(Boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<String> getAllowableValues() {
        return allowableValues;
    }

    public void setAllowableValues(List<String> allowableValues) {
        this.allowableValues = allowableValues;
    }

    public String getParamAccess() {
        return paramAccess;
    }

    public void setParamAccess(String paramAccess) {
        this.paramAccess = paramAccess;
    }
}
