package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * To use Datasonnet scripts in Camel expressions or predicates.
 */
@Metadata(firstVersion = "3.5.0", label = "language,script", title = "Datasonnet")
@XmlRootElement(name = "datasonnet")
@XmlAccessorType(XmlAccessType.FIELD)
public class DatasonnetExpression extends ExpressionDefinition {

    @XmlAttribute(name = "inputMimeType")
    private String inputMimeType;

    @XmlAttribute(name = "outputMimeType")
    private String outputMimeType;

    @XmlAttribute(name = "type")
    private String type;

    public DatasonnetExpression() {
    }

    public DatasonnetExpression(String expression) {
        super(expression);
    }

    public DatasonnetExpression(Expression expression) {
        super(expression);
    }

    @Override
    public String getLanguage() {
        return "datasonnet";
    }

    public String getInputMimeType() {
        return inputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * 
     * @param inputMimeType docs
     */
    public void setInputMimeType(String inputMimeType) {
        this.inputMimeType = inputMimeType;
    }

    public String getOutputMimeType() {
        return outputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * 
     * @param outputMimeType docs
     */
    public void setOutputMimeType(String outputMimeType) {
        this.outputMimeType = outputMimeType;
    }

    /**
     * TODO: 9/3/20 docs
     * 
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * TODO: 9/3/20 docs
     * 
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }
}
