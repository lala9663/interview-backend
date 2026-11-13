package com.interview.common.web;

import java.util.List;

import lombok.Getter;

/** 페이지가 없는 목록 응답: { "items": [...] } */
@Getter
public class ListResponse<T> {

    private final List<T> items;

    public ListResponse(List<T> items) {
        this.items = items;
    }

    public static <T> ListResponse<T> of(List<T> items) {
        return new ListResponse<T>(items);
    }
}
