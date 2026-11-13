package com.interview.common.web;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/** 페이지가 있는 목록 응답: { "items": [...], "page": 0, "size": 20, "total": 132 } */
@Getter
public class PageResponse<T> {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    public PageResponse(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public static int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 이미 메모리에 있는 전체 목록을 잘라 한 페이지로 만든다. */
    public static <T> PageResponse<T> slice(List<T> all, Integer page, Integer size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        int from = Math.min(p * s, all.size());
        int to = Math.min(from + s, all.size());
        return new PageResponse<T>(new ArrayList<T>(all.subList(from, to)), p, s, all.size());
    }
}
