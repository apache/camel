package org.apache.camel.kotlin.model

import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ConvertVariableDefinition

@CamelDslMarker
class ConvertVariableDsl(
    val def: ConvertVariableDefinition
) : OptionalIdentifiedDsl(def) {

    fun toName(toName: String) {
        def.toName = toName
    }

    fun mandatory(mandatory: Boolean) {
        def.mandatory = mandatory.toString()
    }

    fun mandatory(mandatory: String) {
        def.mandatory = mandatory
    }

    fun charset(charset: String) {
        def.charset = charset
    }
}