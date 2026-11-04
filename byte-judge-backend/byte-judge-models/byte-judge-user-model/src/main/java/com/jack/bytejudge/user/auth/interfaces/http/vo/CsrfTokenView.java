package com.jack.bytejudge.user.auth.interfaces.http.vo;

import org.springframework.security.web.csrf.CsrfToken;

/** How the browser must echo the CSRF token on the next state-changing request. */
public record CsrfTokenView(String headerName, String token) {
    public static CsrfTokenView of(CsrfToken token) {
        return new CsrfTokenView(token.getHeaderName(), token.getToken());
    }
}
