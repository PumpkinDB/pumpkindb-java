/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class SignedInteger implements Encodable {
    private final BigInteger value;

    public SignedInteger(BigInteger value) {
        this.value = value;
    }

    public SignedInteger(long val) {
        this(BigInteger.valueOf(val));
    }

    public BigInteger getValue() {
        return value;
    }

    @Override public void encode(ByteBuf buffer) {
        byte[] ba = value.toByteArray();
        ByteBuffer bb = ByteBuffer.allocate(ba.length + 1);
        if (value.compareTo(BigInteger.ZERO) >= 0) {
            bb.put((byte) 1);
        } else {
            bb.put((byte) 0);
        }
        new Data(bb.array()).encode(buffer);
    }
}
