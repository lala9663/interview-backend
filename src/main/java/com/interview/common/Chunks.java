package com.interview.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Oracle 의 IN 절 1000개 제한을 피하려고 id 목록을 나눈다. */
public final class Chunks {

    public static final int IN_LIMIT = 500;

    private Chunks() {
    }

    public static <T> List<List<T>> of(Collection<T> source) {
        List<List<T>> result = new ArrayList<List<T>>();
        List<T> current = new ArrayList<T>();
        for (T item : source) {
            current.add(item);
            if (current.size() == IN_LIMIT) {
                result.add(current);
                current = new ArrayList<T>();
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }
}
