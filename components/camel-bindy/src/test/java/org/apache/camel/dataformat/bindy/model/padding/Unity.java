package org.apache.camel.dataformat.bindy.model.padding;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",")
public class Unity {
    @DataField(pos = 1, pattern = "000")
    public float mandant;
    
    @DataField(pos = 2, pattern = "000")
    public float receiver;

	public float getMandant() {
		return mandant;
	}

	public void setMandant(float mandant) {
		this.mandant = mandant;
	}

	public float getReceiver() {
		return receiver;
	}

	public void setReceiver(float receiver) {
		this.receiver = receiver;
	}

	@Override
	public String toString() {
		return "Unity [mandant=" + mandant + ", receiver=" + receiver + "]";
	}

}
