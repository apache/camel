package org.apache.camel.language.datasonnet

import com.datasonnet.document.{DefaultDocument, Document}
import com.datasonnet.spi.{DataFormatService, Library, PluginException}
import org.apache.camel.Exchange
import sjsonnet.Std.builtin
import sjsonnet.{Materializer, Val}

object CML extends Library {
  val exchange: ThreadLocal[Exchange] = new ThreadLocal[Exchange]

  override def namespace(): String = "cml"

  override def libsonnets(): Set[String] = Set.empty

  override def functions(dataformats: DataFormatService): Map[String, Val.Func] = Map(
    // See: org.apache.camel.language.xpath.XPathBuilder.createPropertiesFunction
    builtin("properties", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) =>
          // use the property placeholder resolver to lookup the property for us
          Val.Str(exchange.get.getContext.resolvePropertyPlaceholders("{{" + value + "}}"))
        case Val.Null => key
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    }),

    builtin("header", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) => valFrom(exchange.get.getMessage.getHeader(value), dataformats)
        case Val.Null => key
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    }),

    builtin("exchangeProperty", "key")((_, _, key: Val) => {
      key match {
        case Val.Str(value) => valFrom(exchange.get.getProperty(value), dataformats)
        case Val.Null => key
        case _ => throw new IllegalArgumentException("Expected String got: " + key.prettyName)
      }
    })
  )

  // TODO: write to map null objs to Val.Null instead NPE
  private def valFrom(obj: AnyRef, dataformats: DataFormatService): Val = {
    val doc: Document[_] = if (obj.isInstanceOf[Document[_]]) obj.asInstanceOf else new DefaultDocument(obj)
    try Materializer.reverse(dataformats.thatAccepts(doc)
      .orElseThrow(() => new IllegalArgumentException("todo"))
      .read(doc))
    catch {
      case e: PluginException => throw new IllegalStateException(e)
    }
  }

  override def modules(dataformats: DataFormatService): Map[String, Val.Obj] = Map.empty
}
