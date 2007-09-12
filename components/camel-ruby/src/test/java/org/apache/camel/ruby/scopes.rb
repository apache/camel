require 'camel'

include_class "org.apache.camel.ruby.RubyCamel"

class MyScopesRoute < Camel::RubyRouteBuilder

  def configure 
    
    from "direct:a"
    
    filter {|e| e.in.headers["foo"] == "bar" }.to "mock:results"

  end

end

RubyCamel.addRouteBuilder(MyScopesRoute.new)



