package com.github.dexluthor.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@ToString
public class Pair<T1, T2> {
    private final T1 key;
    private final T2 value;

    public static <T1, T2> Pair<T1, T2> ofEntry(Map.Entry<? extends T1, ? extends T2> entry) {
        return new Pair<>(entry.getKey(), entry.getValue());
    }

    public static <T1, T2> List<Pair<T1, T2>> ofMap(Map<T1, T2> map) {
        return map.entrySet().stream().map(Pair::ofEntry).collect(Collectors.toList());
    }

}
