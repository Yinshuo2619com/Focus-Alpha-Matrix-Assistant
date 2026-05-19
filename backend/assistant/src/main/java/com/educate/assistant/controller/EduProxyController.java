package com.educate.assistant.controller;

import com.educate.assistant.entity.SchoolConfig;
import com.educate.assistant.service.EduProxyService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/edu-proxy")
@RequiredArgsConstructor
public class EduProxyController {

    private static final String PROXY_SESSION_COOKIE = "EDU_PROXY_SESSION";

    private final EduProxyService eduProxyService;
    private final JdbcTemplate jdbcTemplate;

    @RequestMapping("/{schoolId}/**")
    public ResponseEntity<byte[]> proxy(
            @PathVariable String schoolId,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 1. Resolve school config
        SchoolConfig school = getSchoolConfig(schoolId);
        if (school == null) {
            return ResponseEntity.badRequest().build();
        }

        // 2. Identify user: try JWT auth first, then proxy session cookie
        Long userId = getUserIdFromAuth();
        boolean isNewSession = (userId != null);

        if (userId == null) {
            userId = getUserIdFromCookie(request);
        }

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        // 3. Set proxy session cookie on first auth
        if (isNewSession) {
            Cookie cookie = new Cookie(PROXY_SESSION_COOKIE, String.valueOf(userId));
            cookie.setPath("/api/edu-proxy");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(30 * 60);
            response.addCookie(cookie);
        }

        // 4. Build target URL (strip /api/edu-proxy/{schoolId} prefix)
        String pathWithinProxy = request.getRequestURI()
            .replaceFirst("/api/edu-proxy/" + schoolId, "");
        String queryString = sanitizeQueryString(request.getQueryString());
        String targetUrl = school.getBaseUrl() + pathWithinProxy
            + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");

        // 5. Forward request
        System.out.println("[EduProxy] " + request.getMethod() + " " + targetUrl);
        EduProxyService.ProxyResult result;
        try {
            result = eduProxyService.proxyRequest(
                userId, request.getMethod(), targetUrl, request, school.getBaseUrl());
        } catch (Exception e) {
            String errorHtml = "<html><body><h3>Proxy Error</h3><pre>" + e.getMessage() + "</pre></body></html>";
            return ResponseEntity.status(502)
                .contentType(MediaType.TEXT_HTML)
                .body(errorHtml.getBytes(StandardCharsets.UTF_8));
        }

        // 6. Store cookies from educational system (skip 3xx redirects — they contain session-clearing cookies)
        if (result.statusCode() >= 300 && result.statusCode() < 400) {
            System.out.println("[EduProxy] Skipping cookies from " + result.statusCode() + " redirect (would poison session)");
        } else {
            eduProxyService.storeCookies(userId, result.cookies());
        }
        System.out.println("[EduProxy] Response: " + result.contentType()
            + " (" + (result.body() != null ? result.body().length : 0) + " bytes)"
            + ", cookies: " + result.cookies().size()
            + ", stored: " + eduProxyService.getSessionCookies(userId).size());
        if (!result.cookies().isEmpty()) {
            System.out.println("[EduProxy] Response cookies: " + result.cookies().keySet());
        }

        // 7. Handle 302 redirect: follow server-side, return final page as 200
        //    DO NOT let the browser see the 302 — it would follow to the edu system directly
        //    and the session rotation would cause an infinite loop
        int maxRedirects = 3;
        int redirectCount = 0;
        while (result.statusCode() >= 300 && result.statusCode() < 400
               && result.location() != null && redirectCount < maxRedirects) {
            String location = result.location();
            String redirectUrl;
            if (location.startsWith("http")) {
                redirectUrl = location;
            } else if (location.startsWith("/")) {
                redirectUrl = school.getBaseUrl() + location;
            } else {
                redirectUrl = location;
            }
            redirectCount++;
            System.out.println("[EduProxy] Server-side redirect follow (#" + redirectCount + "): " + redirectUrl);
            try {
                result = eduProxyService.proxyRequest(
                    userId, "GET", redirectUrl, request, school.getBaseUrl());
                eduProxyService.storeCookies(userId, result.cookies());
                System.out.println("[EduProxy] Redirect result: " + result.statusCode()
                    + " (" + (result.body() != null ? result.body().length : 0) + " bytes)");
            } catch (Exception e) {
                System.out.println("[EduProxy] Redirect follow failed: " + e.getMessage());
                String errorHtml = "<html><body><h3>Redirect Error</h3><pre>" + e.getMessage() + "</pre></body></html>";
                return ResponseEntity.status(502)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (redirectCount >= maxRedirects) {
            System.out.println("[EduProxy] Redirect loop detected after " + maxRedirects + " hops, returning 502");
            String errorHtml = "<html><body><h3>Redirect Loop</h3><p>Too many redirects — possible session loop</p></body></html>";
            return ResponseEntity.status(502)
                .contentType(MediaType.TEXT_HTML)
                .body(errorHtml.getBytes(StandardCharsets.UTF_8));
        }

        // 8. HTML post-processing: inject JS to rewrite URLs and prevent new windows
        byte[] responseBody = result.body();
        if (result.contentType() != null && result.contentType().contains("text/html") && responseBody != null) {
            String html = new String(responseBody, StandardCharsets.UTF_8);

            String injectedScript = "<script>" +
                "(function(){" +
                "  if(window.__eduProxyPatched)return;" +
                "  window.__eduProxyPatched=true;" +
                // Helper: rewrite URL to go through Java proxy
                "  function proxyUrl(u){" +
                "    if(typeof u!=='string')return u;" +
                "    if(u.indexOf('/api/edu-proxy/')===0)return u;" +
                "    u=u.replace(/^https?:\\/\\/[^\\/]+\\/student/,'/api/edu-proxy/default/student');" +
                "    if(u.indexOf('/student/')===0&&u.indexOf('/api/')!==0)u='/api/edu-proxy/default'+u;" +
                "    return u;" +
                "  };" +
                // Patch jQuery.ajax to rewrite URLs (must run before any $.ajax calls)
                "  function patchJQuery(){" +
                "    var jq=window.jQuery||window.$;" +
                "    if(!jq||jq.__eduProxyPatched)return;" +
                "    jq.__eduProxyPatched=true;" +
                "    var origAjax=jq.ajax;" +
                "    jq.ajax=function(opts){" +
                "      if(opts&&opts.url){" +
                "        console.log('[EduProxy-jQuery] ajax url before:',opts.url);" +
                "        opts.url=proxyUrl(opts.url);" +
                "        console.log('[EduProxy-jQuery] ajax url after:',opts.url);" +
                "      }" +
                "      return origAjax.apply(this,arguments);" +
                "    };" +
                "    console.log('[EduProxy] jQuery.ajax patched');" +
                "    jq.ajaxSetup({" +
                "      beforeSend:function(xhr){" +
                "        xhr.setRequestHeader('X-Requested-With','XMLHttpRequest');" +
                "      }" +
                "    });" +
                "  };" +
                // Try patching jQuery now, or wait for it to load
                "  patchJQuery();" +
                "  if(!window.jQuery&&!window.$){" +
                "    var observer=new MutationObserver(function(mutations){" +
                "      if(window.jQuery||window.$){" +
                "        patchJQuery();" +
                "        observer.disconnect();" +
                "      }" +
                "    });" +
                "    observer.observe(document.documentElement,{childList:true,subtree:true});" +
                "  };" +
                // Patch fetch() to rewrite URLs
                "  if(window.fetch){" +
                "    var origFetch=window.fetch;" +
                "    window.fetch=function(url,opts){" +
                "      if(typeof url==='string')url=proxyUrl(url);" +
                "      return origFetch.apply(this,arguments);" +
                "    };" +
                "    console.log('[EduProxy] fetch patched');" +
                "  };" +
                // Patch XMLHttpRequest.open() to rewrite URLs
                "  var origXHROpen=XMLHttpRequest.prototype.open;" +
                "  XMLHttpRequest.prototype.open=function(method,url){" +
                "    if(typeof url==='string')url=proxyUrl(url);" +
                "    return origXHROpen.apply(this,arguments);" +
                "  };" +
                "  console.log('[EduProxy] XMLHttpRequest.open patched');" +
                // Intercept link clicks — must preventDefault + navigate manually
                "  document.addEventListener('click',function(e){" +
                "    var a=e.target.closest('a');" +
                "    if(!a)return;" +
                "    var href=a.getAttribute('href');" +
                "    if(!href||href.charAt(0)==='#')return;" +
                "    e.preventDefault();e.stopPropagation();" +
                "    var rewritten=proxyUrl(href);" +
                "    console.log('[EduProxy] link click:',href,'->',rewritten);" +
                "    window.location.href=rewritten;" +
                "  },true);" +
                // Override window.open
                "  window.open=function(url){" +
                "    window.location.href=proxyUrl(url);" +
                "  };" +
                "  console.log('[EduProxy] Script injected');" +
                "})();" +
                "</script>";
            if (html.contains("<head>")) {
                html = html.replace("<head>", "<head>\n" + injectedScript);
            } else if (html.contains("<HEAD>")) {
                html = html.replace("<HEAD>", "<HEAD>\n" + injectedScript);
            } else {
                html = injectedScript + "\n" + html;
            }

            // Rewrite CONTEXT_PATH so educational system JS constructs proxy URLs
            html = html.replaceAll(
                "window\\.CONTEXT_PATH\\s*=\\s*['\"]/?student['\"]",
                "window.CONTEXT_PATH = '/api/edu-proxy/default/student'");

            // Rewrite static resource paths: /student/... → /api/edu-proxy/default/student/...
            html = html.replaceAll(
                "((?:src|href|action)=[\"'])/(student/[^\"']+)",
                "$1/api/edu-proxy/default/$2");

            // Rewrite absolute URLs to edu system domain
            html = html.replaceAll(
                "https?://jwxt\\.aqnu\\.edu\\.cn(/student/[^\"'\\s]+)",
                "/api/edu-proxy/default$1");

            responseBody = html.getBytes(StandardCharsets.UTF_8);
        }

        // 9. Build response, strip frame-blocking headers
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(result.contentType()))
            .header("X-Frame-Options", "ALLOWALL")
            .header("Content-Security-Policy", "frame-ancestors *")
            .body(responseBody);
    }

    private Long getUserIdFromAuth() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                return jdbcTemplate.queryForObject(
                    "SELECT id FROM user WHERE username = ?", Long.class, auth.getName());
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Long getUserIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (PROXY_SESSION_COOKIE.equals(cookie.getName())) {
                    try {
                        return Long.parseLong(cookie.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null) return null;
        String[] params = queryString.split("&");
        StringBuilder sb = new StringBuilder();
        for (String param : params) {
            if (!param.startsWith("token=")) {
                if (sb.length() > 0) sb.append("&");
                sb.append(param);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private SchoolConfig getSchoolConfig(String schoolId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM school_config WHERE school_id = ? AND enabled = 1",
                (rs, rowNum) -> {
                    SchoolConfig sc = new SchoolConfig();
                    sc.setId(rs.getLong("id"));
                    sc.setSchoolId(rs.getString("school_id"));
                    sc.setSchoolName(rs.getString("school_name"));
                    sc.setBaseUrl(rs.getString("base_url"));
                    sc.setLoginPath(rs.getString("login_path"));
                    sc.setSchedulePath(rs.getString("schedule_path"));
                    sc.setEnabled(rs.getInt("enabled"));
                    return sc;
                },
                schoolId);
        } catch (Exception e) {
            return null;
        }
    }
}
