/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.camel.dataformat.any23;

import java.io.ByteArrayOutputStream;
import org.apache.any23.writer.NTriplesWriter;
import org.apache.any23.writer.TripleHandler;

/**
 *
 * @author joe
 */
public class Any23Parameters {
  
private  ByteArrayOutputStream OUT;
//public static  final TripleHandler TRIPLEHANDLER ;

private TripleHandler triplehandler ;

public TripleHandler getTripleHandlerOutput (){
 return triplehandler;
}

public void setTripleHandlerOutput (TripleHandler triplehandler ){
this.triplehandler = triplehandler;
}
 
public Any23Parameters (ByteArrayOutputStream out) {
 this.OUT = out;
 this.triplehandler = new NTriplesWriter(out);
}
  
}
