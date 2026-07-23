package com.example.accounting.config;

import com.example.accounting.common.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Webflux 全局响应包装过滤器
 * 将 API 接口的响应体统一包装为 ApiResponse<T> 格式
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiResponseWebFilter implements WebFilter {

    private final ObjectMapper objectMapper;

    public ApiResponseWebFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 只处理 /api/ 路径下的请求
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // 只处理 200 状态码的成功响应
                if (getStatusCode() != HttpStatus.OK) {
                    return super.writeWith(body);
                }

                return DataBufferUtils.join(body)
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);

                            String originalBody = new String(bytes, StandardCharsets.UTF_8);

                            // 检查是否已经是 ApiResponse 格式（如异常处理器返回的）
                            if (isApiResponse(originalBody)) {
                                DataBuffer newBuffer = bufferFactory().wrap(bytes);
                                return super.writeWith(Mono.just(newBuffer));
                            }

                            // 包装为 ApiResponse
                            try {
                                String wrappedBody = wrapResponse(originalBody);
                                byte[] wrappedBytes = wrappedBody.getBytes(StandardCharsets.UTF_8);

                                getHeaders().setContentLength(wrappedBytes.length);
                                getHeaders().setContentType(MediaType.APPLICATION_JSON);

                                DataBuffer newBuffer = bufferFactory().wrap(wrappedBytes);
                                return super.writeWith(Mono.just(newBuffer));
                            } catch (JsonProcessingException e) {
                                return Mono.error(e);
                            }
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 将原始响应体包装为 ApiResponse 格式的 JSON 字符串
     */
    private String wrapResponse(String originalBody) throws JsonProcessingException {
        if (originalBody == null || originalBody.trim().isEmpty()) {
            return objectMapper.writeValueAsString(ApiResponse.ok());
        }

        // 尝试解析为 JSON（对象或数组）
        try {
            JsonNode jsonNode = objectMapper.readTree(originalBody);
            return objectMapper.writeValueAsString(ApiResponse.ok(jsonNode));
        } catch (JsonProcessingException e) {
            // 不是 JSON，当作纯字符串处理
            return objectMapper.writeValueAsString(ApiResponse.ok(originalBody));
        }
    }

    /**
     * 判断响应体是否已经是 ApiResponse 格式
     */
    private boolean isApiResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isObject()
                    && node.has("code")
                    && node.has("message")
                    && node.has("data");
        } catch (Exception e) {
            return false;
        }
    }
}
