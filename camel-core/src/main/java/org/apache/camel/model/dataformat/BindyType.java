package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlEnum;

/**
 * Represents the different types of bindy data formats.
 *
 * @version $Revision$
 */
@XmlType
@XmlEnum(String.class)
public enum BindyType {

    Csv, KeyValue
}
