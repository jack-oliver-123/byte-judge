package com.jack.bytejudge.common.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jack.bytejudge.common.core.response.ApiError;
import com.jack.bytejudge.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class CommonErrorAdviceTest {
    private static final String SENSITIVE = "private-password-jdbc:secret-address";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ProbeAdvice())
                .addFilters(new RequestLogFilter(false), new ContractErrorFilter())
                .build();
    }

    static Stream<Object[]> failures() {
        return Stream.of(
                new Object[]{get("/number").param("value", "not-a-number"), ApiError.INVALID_REQUEST},
                new Object[]{post("/body").contentType(MediaType.APPLICATION_JSON).content("{"),
                        ApiError.INVALID_REQUEST},
                new Object[]{get("/missing"), ApiError.NOT_FOUND},
                new Object[]{post("/number"), ApiError.METHOD_NOT_ALLOWED},
                new Object[]{post("/body").contentType(MediaType.TEXT_PLAIN).content("value"),
                        ApiError.UNSUPPORTED_MEDIA_TYPE},
                new Object[]{get("/failure"), ApiError.INTERNAL_ERROR});
    }

    @ParameterizedTest
    @MethodSource("failures")
    void frameworkErrorsUseTheFrozenHttpContract(MockHttpServletRequestBuilder request, ApiError error)
            throws Exception {
        var response = mvc.perform(request.header(RequestLogFilter.HEADER, "framework-contract"))
                .andReturn().getResponse();
        var body = JSON.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(error.status());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader(RequestLogFilter.HEADER)).isEqualTo("framework-contract");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(body.propertyNames()).containsExactlyInAnyOrderElementsOf(
                List.of("code", "data", "message", "requestId", "serverTime"));
        assertThat(body.path("code").asInt()).isEqualTo(error.code());
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("message").asText()).isEqualTo(error.message());
        assertThat(body.path("requestId").asText()).isEqualTo("framework-contract");
        assertThat(body.path("serverTime").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
        assertThat(response.getContentAsString()).doesNotContain(SENSITIVE, "IllegalStateException");
    }

    @Test
    void serviceSpecificHandlerWinsOverTheInheritedFallback() throws Exception {
        var response = mvc.perform(get("/credentials")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asInt()).isEqualTo(40101);
    }

    @Test
    void fallbackLogsOnlyCorrelationErrorAndExceptionType() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("bytejudge.http");
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            mvc.perform(get("/failure").header(RequestLogFilter.HEADER, "redaction-test"));
            assertThat(events.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("request failed", "requestId=redaction-test",
                        "error=INTERNAL_ERROR", "type=java.lang.IllegalStateException");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(events.list).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(SENSITIVE));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/number")
        int number(@RequestParam int value) {
            return value;
        }

        @PostMapping(value = "/body", consumes = "application/json")
        Body body(@RequestBody Body body) {
            return body;
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException(SENSITIVE);
        }

        @GetMapping("/credentials")
        void credentials() {
            throw new BadCredentialsException(SENSITIVE);
        }
    }

    record Body(String value) {
    }

    @RestControllerAdvice
    static class ProbeAdvice extends CommonErrorAdvice {
        @ExceptionHandler(BadCredentialsException.class)
        ResponseEntity<ApiResponse<Void>> credentials(HttpServletRequest request) {
            return respond(ApiError.INVALID_CREDENTIALS, request);
        }
    }
}
