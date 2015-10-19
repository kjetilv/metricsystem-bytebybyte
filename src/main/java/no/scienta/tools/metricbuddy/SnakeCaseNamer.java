package no.scienta.tools.metricbuddy;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

final class SnakeCaseNamer implements MetricsBuddy.MetricNameStrategy {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String metricName(Method method) {
        return cache.computeIfAbsent(method.getName(), SnakeCaseNamer::snakeCaseName);
    }

    private static String snakeCaseName(String name) {
        return name.chars()
                .mapToObj(i -> (char) i)
                .flatMap(c -> isUpperCase(c)
                        ? Stream.<Character>of('-', toLowerCase(c))
                        : Stream.of(c))
                .reduce(new StringBuilder(),
                        (sb, c) -> sb.append(c), StringBuilder::append)
                .toString();
    }
}
