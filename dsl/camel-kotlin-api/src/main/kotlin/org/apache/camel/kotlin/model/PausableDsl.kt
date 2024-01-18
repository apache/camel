package org.apache.camel.kotlin.model

import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.PausableDefinition
import org.apache.camel.resume.ConsumerListener
import java.util.function.Predicate

@CamelDslMarker
class PausableDsl(
    val def: PausableDefinition
) : OptionalIdentifiedDsl(def) {

    fun consumerListener(consumerListener: ConsumerListener<*, *>) {
        def.consumerListener(consumerListener)
    }

    fun consumerListener(consumerListener: String) {
        def.consumerListener(consumerListener)
    }

    fun untilCheck(untilCheck: Predicate<*>) {
        def.untilCheck(untilCheck)
    }

    fun untilCheck(untilCheck: String) {
        def.untilCheck(untilCheck)
    }
}