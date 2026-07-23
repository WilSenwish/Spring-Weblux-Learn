package com.example.accounting.config;

import com.example.accounting.common.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 对所有 Controller 方法的返回值进行包装
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 已经是 ApiResponse 的不再包装
        if (body instanceof ApiResponse) {
            return body;
        }
        ApiResponse<Object> apiResponse = ApiResponse.ok(body);
        // String 类型需要手动序列化为 JSON 字符串，避免 StringHttpMessageConverter 类型转换异常
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(apiResponse);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("响应序列化失败", e);
            }
        }
        return apiResponse;
    }
}
