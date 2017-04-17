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

public class UnsignedInteger implements Encodable {
    private final BigInteger value;

    public UnsignedInteger(BigInteger value) {
        if (value.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("UnsignedInteger can't be initialized with a negative number");
        }
        this.value = value;
    }

    public UnsignedInteger(long val) {
        this(BigInteger.valueOf(val));
    }

    public BigInteger getValue() {
        return value;
    }

    @Override public void encode(ByteBuf buffer) {
        new Data(value.toByteArray()).encode(buffer);
    }
}
