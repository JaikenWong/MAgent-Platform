package com.magent.platform.service.a2a;

import com.magent.platform.dto.a2a.Message;
import com.magent.platform.dto.a2a.Part;
import com.magent.platform.dto.a2a.TextPart;

/** 辅助: 从 Message.parts 里抓文本 (concat 所有 TextPart). */
public final class A2AMappers {

    private A2AMappers() {}

    public static String firstText(Message m) {
        if (m == null || m.parts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Part p : m.parts()) {
            if (p instanceof TextPart tp) sb.append(tp.text());
        }
        return sb.toString();
    }
}