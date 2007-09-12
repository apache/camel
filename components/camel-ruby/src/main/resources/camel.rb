require "java"

module Camel

  include_class "org.apache.camel.impl.DefaultCamelContext"
  include_class "org.apache.camel.impl.ExpressionSupport"
  include_class "org.apache.camel.model.RouteType"
  include_class "org.apache.camel.ruby.ScriptRouteBuilder"

  class RubyRouteBuilder < ScriptRouteBuilder

    # def from(uri)
    #   @node = super.from(uri)
    #   @node
    # end
    # 
    # def to(uri)
    #   @node = @node.to(uri)
    #   @node
    # end
    # 
    
    
    def filter(params={}, &predicate)
      e = expression(&predicate)
      answer = getNode().filter(e)
      setNode(answer)
      return answer
    end
    
    def aaaafilter2(predicate, &block)
      puts "Called filter2 with #{predicate}"
      node = getNode()
      setNode(node.filter(predicate))
      block.call(getNode())
      setNode(node)
    end
    
    def expression(&block)
      e = BlockExpression.new
      e.value(&block)
    end
  end
  
  class BlockExpression < ExpressionSupport

    def value(&block)
      @block = block
      self
    end
    
    def evaluate(exchange)
      @block.call(exchange)
    end
    
    def assertionFailureMessage(text)
      text
    end
    
    def to_s
      @block.to_s
    end
  end
end

