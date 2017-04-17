/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

public class Instruction implements Encodable {
    private final String value;
    private final int length;

    /**
     * @param value
     * @throws IllegalArgumentException if an instruction contains less than 1 or more than 127 characters
     */
    public Instruction(String value) {
        length = value.getBytes().length;
        if (length < 1 || length > 127) {
            throw new IllegalArgumentException();
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override public void encode(ByteBuf buffer) {
        buffer.writeByte((byte) (length | 0x80));
        buffer.writeBytes(value.getBytes());
    }
}
