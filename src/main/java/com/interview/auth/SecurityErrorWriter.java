package com.interview.auth;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import com.interview.common.json.Json;
import com.interview.common.web.ErrorResponse;

/** 필터 단계(컨트롤러 밖)에서 계약의 오류 응답을 직접 쓴다. */
public final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.write(new ErrorResponse(code, message)));
    }
}
