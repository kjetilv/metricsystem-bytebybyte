package no.scienta.tools.metricbuddy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

public class AbstractSeparatorNamer implements MetricsBuddy.MetricNameStrategy {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final char separator;

    protected AbstractSeparatorNamer(char c) {
        separator = '-';
    }

    @Override
    public String metricName(Method method) {
        return cache.computeIfAbsent(method.getName(), name -> casedName(name, '-'));
    }

    private String casedName(String name, char separator) {
        return name.chars()
                .mapToObj(i -> (char) i)
                .flatMap(c -> isUpperCase(c)
                        ? Stream.<Character>of(separator, toLowerCase(c))
                        : Stream.of(c))
                .reduce(new StringBuilder(),
                        (sb, c) -> sb.append(c), StringBuilder::append)
                .toString();
    }
}
