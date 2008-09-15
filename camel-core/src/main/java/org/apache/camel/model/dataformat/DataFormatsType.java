package org.apache.camel.model.dataformat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Represents the XML type for a collection of DataFormats.
 */
@XmlRootElement(name = "dataFormats")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatsType {

    // TODO cannot use @XmlElementRef as it doesn't allow optional properties
    // @XmlElementRef    
    @XmlElements({
        @XmlElement(required = false, name = "artixDS", type = ArtixDSDataFormat.class),
        @XmlElement(required = false, name = "csv", type = CsvDataFormat.class),
        @XmlElement(required = false, name = "flatpack", type = FlatpackDataFormat.class),
        @XmlElement(required = false, name = "hl7", type = HL7DataFormat.class),
        @XmlElement(required = false, name = "jaxb", type = JaxbDataFormat.class),
        @XmlElement(required = false, name = "serialization", type = SerializationDataFormat.class),
        @XmlElement(required = false, name = "string", type = StringDataFormat.class),
        @XmlElement(required = false, name = "xmlBeans", type = XMLBeansDataFormat.class),
        @XmlElement(required = false, name = "xstream", type = XStreamDataFormat.class)}
        )
    private List<DataFormatType> dataFormats;

    
    public void setDataFormats(List<DataFormatType> dataFormats) {
        this.dataFormats = dataFormats;
    }

    public List<DataFormatType> getDataFormats() {
        return dataFormats;
    }

    /***
     * @return A Map of the contained DataFormatType's indexed by id.
     */
    public Map<String, DataFormatType> asMap() {
        Map<String, DataFormatType> dataFormatsAsMap = new HashMap<String, DataFormatType>();
        for (DataFormatType dataFormatType : getDataFormats()) {
            dataFormatsAsMap.put(dataFormatType.getId(), dataFormatType);
        }
        return dataFormatsAsMap;
    }     
}
