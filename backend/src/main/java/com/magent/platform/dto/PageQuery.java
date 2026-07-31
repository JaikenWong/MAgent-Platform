package com.magent.platform.dto;

public record PageQuery(
        Integer page,
        Integer size
) {
    public int safePage() { return page == null || page < 1 ? 1 : page; }
    public int safeSize() { return size == null || size < 1 ? 20 : Math.min(size, 200); }
}