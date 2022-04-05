package library;

import java.util.*;
import java.util.function.Function;

import com.datasonnet.header.Header;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import sjsonnet.Val;

@Component
@Configuration
public class TestLib extends Library {

    private static final TestLib INSTANCE = new TestLib();

    public TestLib() {
    }

    @Bean
    @Scope("singleton")
    public static TestLib getInstance() {
        return INSTANCE;
    }

    @Override
    public String namespace() {
        return "testlib";
    }

    @Override
    public Map<String, Val.Func> functions(DataFormatService dataFormats, Header header) {
        Map<String, Val.Func> answer = new HashMap<>();
        answer.put("sayHello", makeSimpleFunc(
                Collections.emptyList(), //parameters list
                new Function<List<Val>, Val>() {
                    @Override
                    public Val apply(List<Val> vals) {
                        return new Val.Str("Hello, World");
                    }
                }));
        return answer;
    }

    @Override
    public Map<String, Val.Obj> modules(DataFormatService dataFormats, Header header) {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> libsonnets() {
        return Collections.emptySet();
    }
}
