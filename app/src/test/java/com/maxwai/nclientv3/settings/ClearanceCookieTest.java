package com.maxwai.nclientv3.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 4.7.1: the Cloudflare WebView must close once cf_clearance exists (the new site has no csrftoken). */
public class ClearanceCookieTest {

    @Test
    public void cfClearanceEndsTheChallenge() {
        assertTrue(CustomInterceptor.hasClearanceCookie("cf_clearance=abc; other=1"));
        assertTrue(CustomInterceptor.hasClearanceCookie("a=1; cf_clearance=xyz"));
        assertTrue(CustomInterceptor.hasClearanceCookie("csrftoken=legacy"));
    }

    @Test
    public void otherCookiesDoNot() {
        assertFalse(CustomInterceptor.hasClearanceCookie(null));
        assertFalse(CustomInterceptor.hasClearanceCookie(""));
        assertFalse(CustomInterceptor.hasClearanceCookie("access_token=t; refresh_token=r"));
        assertFalse(CustomInterceptor.hasClearanceCookie("cf_clearance="));
        assertFalse(CustomInterceptor.hasClearanceCookie("not_cf_clearance=1"));
    }
}
