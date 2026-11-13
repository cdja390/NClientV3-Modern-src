/*
 * Copyright 2026 yosefario-dev and NClientV3 contributors
 * Copyright 2026 NClientV3-Modern contributors (modifications)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Based on loginapi/PowSolver.java from yosefario-dev/NClientV3
 * (https://github.com/yosefario-dev/NClientV3, Apache-2.0).
 * Modified for NClientV3 Modern: package, cancellation hook. See docs/PATCHED_FILES.md
 */
package com.maxwai.nclientv3.loginapi;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Proof of work for nhentai's /api/v2/pow: find the decimal nonce such that
 * SHA-256(UTF-8(challenge + nonce)) starts with {@code difficulty} zero bits.
 */
public final class PowSolver {

    private PowSolver() {
    }

    @NonNull
    public static String solve(@NonNull String challenge, int difficulty) {
        if (difficulty < 0 || difficulty > 256)
            throw new IllegalArgumentException("difficulty out of range: " + difficulty);
        if (difficulty == 0) return "0";
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] challengeBytes = challenge.getBytes(StandardCharsets.UTF_8);
        long nonce = 0;
        while (true) {
            if ((nonce & 0xFFFF) == 0 && Thread.currentThread().isInterrupted())
                throw new IllegalStateException("PoW cancelled");
            String nonceStr = Long.toString(nonce);
            digest.reset();
            digest.update(challengeBytes);
            digest.update(nonceStr.getBytes(StandardCharsets.US_ASCII));
            if (hasLeadingZeroBits(digest.digest(), difficulty)) return nonceStr;
            nonce++;
        }
    }

    public static boolean verify(@NonNull String challenge, @NonNull String nonce, int difficulty) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(challenge.getBytes(StandardCharsets.UTF_8));
            digest.update(nonce.getBytes(StandardCharsets.US_ASCII));
            return hasLeadingZeroBits(digest.digest(), difficulty);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    static boolean hasLeadingZeroBits(byte[] hash, int bits) {
        int fullBytes = bits >>> 3;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        int remaining = bits & 7;
        if (remaining == 0) return true;
        int next = hash[fullBytes] & 0xFF;
        return (next >>> (8 - remaining)) == 0;
    }
}
