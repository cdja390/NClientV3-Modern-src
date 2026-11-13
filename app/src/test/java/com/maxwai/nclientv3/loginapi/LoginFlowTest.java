package com.maxwai.nclientv3.loginapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.maxwai.nclientv3.utility.MiniJson;

import org.junit.Test;

/** 4.7.1 login fixes: refresh token and profile come straight from the token response. */
public class LoginFlowTest {

    @Test
    public void tokenResponseKeepsRefreshTokenAndUser() {
        AuthApi.LoginTokens t = AuthApi.parseTokens(MiniJson.parseObject(
            "{\"access_token\":\"a.b.c\",\"refresh_token\":\" r2 \",\"user\":{\"id\":42,\"username\":\"qalan\","
                + "\"slug\":\"qalan-slug\",\"avatar_url\":\"avatars/42.png\"}}"));
        assertNotNull(t);
        assertEquals("r2", t.refreshToken);
        User u = User.fromApi(t.user);
        assertNotNull(u);
        assertEquals(42, u.getId());
        assertEquals("qalan", u.getUsername());
        assertEquals("qalan-slug", u.getCodename());
    }

    @Test
    public void emptyRefreshTokenIsNull() {
        AuthApi.LoginTokens t = AuthApi.parseTokens(MiniJson.parseObject(
            "{\"access_token\":\"x\",\"refresh_token\":\"\"}"));
        assertNotNull(t);
        assertNull(t.refreshToken);
        assertNull(t.user);
    }

    @Test
    public void userMeResponseParses() {
        User u = User.fromApi(MiniJson.parseObject(
            "{\"id\":7,\"username\":\"bob\",\"slug\":\"\",\"avatar_url\":\"\",\"theme\":\"black\"}"));
        assertNotNull(u);
        assertEquals("bob", u.getCodename());
        User wrapped = User.fromApi(MiniJson.parseObject("{\"user\":{\"id\":8,\"username\":\"amy\",\"slug\":\"amy\"}}"));
        assertNotNull(wrapped);
        assertEquals(8, wrapped.getId());
    }

    @Test
    public void unusableProfilesAreRejected() {
        assertNull(User.fromApi(null));
        assertNull(User.fromApi(MiniJson.parseObject("{\"error\":\"Authentication required\"}")));
        assertNull(User.fromApi(MiniJson.parseObject("{\"id\":0,\"username\":\"x\"}")));
        assertNull(User.fromApi(MiniJson.parseObject("{\"id\":3,\"username\":\" \"}")));
    }
}
