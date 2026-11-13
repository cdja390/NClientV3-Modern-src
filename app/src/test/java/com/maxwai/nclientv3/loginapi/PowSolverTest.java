package com.maxwai.nclientv3.loginapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class PowSolverTest {

    @Test
    public void leadingZeroBitsCountsWholeAndPartialBytes() {
        byte[] hash = new byte[32];
        hash[0] = 0;
        hash[1] = 0x0F; // 0000 1111 -> 12 leading zero bits in total
        assertTrue(PowSolver.hasLeadingZeroBits(hash, 8));
        assertTrue(PowSolver.hasLeadingZeroBits(hash, 12));
        assertFalse(PowSolver.hasLeadingZeroBits(hash, 13));
        assertTrue(PowSolver.hasLeadingZeroBits(hash, 0));
    }

    @Test
    public void solvedNonceSatisfiesTheDifficulty() throws Exception {
        String challenge = "nclient-test-challenge";
        int difficulty = 12;
        String nonce = PowSolver.solve(challenge, difficulty);
        assertTrue(PowSolver.verify(challenge, nonce, difficulty));
        // Independent check with a fresh digest: SHA-256(challenge + decimal nonce).
        byte[] d = MessageDigest.getInstance("SHA-256")
            .digest((challenge + nonce).getBytes(StandardCharsets.UTF_8));
        assertEquals(0, d[0]);
        assertEquals(0, (d[1] & 0xFF) >>> 4);
        // The nonce is a plain decimal string.
        assertTrue(nonce.matches("\\d+"));
    }

    @Test
    public void solverReturnsTheSmallestNonce() {
        String challenge = "abc";
        String nonce = PowSolver.solve(challenge, 8);
        long n = Long.parseLong(nonce);
        for (long k = 0; k < n; k++) assertFalse(PowSolver.verify(challenge, Long.toString(k), 8));
    }

    @Test
    public void zeroDifficultyNeedsNoWork() {
        assertEquals("0", PowSolver.solve("anything", 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleDifficulty() {
        PowSolver.solve("x", 257);
    }
}
