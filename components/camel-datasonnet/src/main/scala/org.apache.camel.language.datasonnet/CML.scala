package org.apache.camel.language

import com.datasonnet.Mapper
import com.datasonnet.wrap.Library.library
import org.apache.camel.Exchange
import sjsonnet.Std.builtin

object CML {
  val exchange: ThreadLocal[Exchange] = new ThreadLocal[Exchange]

  val libraries = Map(
    "Core" -> library(

      // See: org.apache.camel.language.xpath.XPathBuilder.createPropertiesFunction
      builtin("properties", "key") ((_, _, key: String) => {
        var answer = key

        if (key != null) {
          answer = try {
            // use the property placeholder resolver to lookup
            // the property for us
            exchange.get.getContext.resolvePropertyPlaceholders("{{" + key + "}}")
          } catch {
            case e: Exception =>
              throw new IllegalArgumentException(e)
          }
        }

        answer
      })
    )
  )

  val asAdditionalLib = Map("CML" -> Mapper.objectify(libraries))
}
