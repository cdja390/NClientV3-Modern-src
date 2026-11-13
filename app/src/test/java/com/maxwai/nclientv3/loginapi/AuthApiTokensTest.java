package com.maxwai.nclientv3.loginapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.maxwai.nclientv3.utility.MiniJson;

import org.junit.Test;

public class AuthApiTokensTest {

    @Test
    public void readsTheTokenResponse() {
        AuthApi.LoginTokens t = AuthApi.parseTokens(MiniJson.parseObject(
            "{\"access_token\":\"a.b.c\",\"refresh_token\":\"r1\",\"user\":{\"id\":5,\"username\":\"x\"}}"));
        assertEquals("a.b.c", t.accessToken);
        assertEquals("r1", t.refreshToken);
    }

    @Test
    public void toleratesNestedTokens() {
        AuthApi.LoginTokens t = AuthApi.parseTokens(MiniJson.parseObject(
            "{\"tokens\":{\"access\":\"acc\",\"refresh\":\"ref\"}}"));
        assertEquals("acc", t.accessToken);
        assertEquals("ref", t.refreshToken);
    }

    @Test
    public void missingAccessTokenIsNull() {
        assertNull(AuthApi.parseTokens(MiniJson.parseObject("{\"user\":{}}")));
        assertNull(AuthApi.parseTokens(null));
    }

    @Test
    public void errorBodiesAreRead() {
        assertEquals("Invalid CAPTCHA solution",
            AuthApi.errorOf(MiniJson.parseObject("{\"error\":\"Invalid CAPTCHA solution\"}")));
        assertTrue(new AuthApi.ApiException(400, "Invalid CAPTCHA solution").isCaptchaRejected());
        assertEquals("bad", AuthApi.errorOf(MiniJson.parseObject("{\"detail\":\"bad\"}")));
    }
}
