require 'camel'

include_class "org.apache.camel.ruby.RubyCamel"

class MyRoute < Camel::RubyRouteBuilder

  def configure 
    
    from "direct:a"
    
    filter { |e|
      e.in.headers["foo"] == "bar"
    }

    # TODO this should not use the filter!
    to "mock:results"

  end

end

RubyCamel.addRouteBuilder(MyRoute.new)



