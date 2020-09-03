package org.apache.camel.language

import com.datasonnet.spi.{DataFormatService, Library}
import org.apache.camel.Exchange
import sjsonnet.Std.builtin
import sjsonnet.Val

object CML extends Library {
  val exchange: ThreadLocal[Exchange] = new ThreadLocal[Exchange]

  override def namespace(): String = "cml"

  override def libsonnets(): Set[String] = Set.empty

  override def functions(dataFormats: DataFormatService): Map[String, Val.Func] = Map(
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

  override def modules(dataFormats: DataFormatService): Map[String, Val.Obj] = Map.empty
}
