package com.maxwai.nclientv3.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MiniJsonTest {

    @Test
    public void roundTripsNestedValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "a \"quoted\" \\ line\nbreak 中文");
        m.put("n", 1234567890123L);
        m.put("d", 2.5);
        m.put("b", true);
        m.put("z", null);
        m.put("list", Arrays.asList(1L, "two", null));
        String json = MiniJson.write(m);
        Object back = MiniJson.parse(json);
        assertEquals(m, back);
    }

    @Test
    public void parsesUnicodeEscapes() {
        Map<String, Object> m = MiniJson.parseObject("{\"k\":\"\\u4e2d\\u6587\"}");
        assertEquals("中文", MiniJson.optString(m, "k"));
    }

    @Test
    public void invalidInputGivesNull() {
        assertNull(MiniJson.parseObject("{\"k\":"));
        assertNull(MiniJson.parseObject("[1,2]"));
        assertNull(MiniJson.parseObject(""));
    }

    @Test
    public void optLongAcceptsStrings() {
        Map<String, Object> m = MiniJson.parseObject("{\"a\":\"42\",\"b\":7}");
        assertEquals(42L, MiniJson.optLong(m, "a", 0));
        assertEquals(7L, MiniJson.optLong(m, "b", 0));
        assertEquals(-1L, MiniJson.optLong(m, "c", -1));
    }

    @Test
    public void arraysParse() {
        Object o = MiniJson.parse(" [ {\"a\":1} , [] ] ");
        assertTrue(o instanceof List);
        assertEquals(2, ((List<?>) o).size());
    }
}
