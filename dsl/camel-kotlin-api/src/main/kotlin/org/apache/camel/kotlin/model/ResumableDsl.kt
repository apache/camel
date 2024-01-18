package org.apache.camel.kotlin.model

import org.apache.camel.LoggingLevel
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.ResumableDefinition
import org.apache.camel.resume.ResumeStrategy
import org.apache.camel.resume.ResumeStrategyConfiguration
import org.apache.camel.resume.ResumeStrategyConfigurationBuilder

@CamelDslMarker
class ResumableDsl(
    val def: ResumableDefinition
) : OptionalIdentifiedDsl(def) {

    fun resumeStrategy(resumeStrategy: String) {
        def.resumeStrategy(resumeStrategy)
    }

    fun resumeStrategy(resumeStrategy: ResumeStrategy) {
        def.resumeStrategy(resumeStrategy)
    }

    fun loggingLevel(loggingLevel: LoggingLevel) {
        def.loggingLevel = loggingLevel.name
    }

    fun loggingLevel(loggingLevel: String) {
        def.loggingLevel = loggingLevel
    }

    fun intermittent(intermittent: Boolean) {
        def.intermittent = intermittent.toString()
    }

    fun intermittent(intermittent: String) {
        def.intermittent = intermittent
    }

    fun configuration(configuration: ResumeStrategyConfigurationBuilder<out ResumeStrategyConfigurationBuilder<*, *>, out ResumeStrategyConfiguration>) {
        def.configuration(configuration)
    }
}