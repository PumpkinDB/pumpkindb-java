/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

public class Data implements Encodable {

    private final byte[] data;

    public Data(byte[] data) {this.data = data;}

    public byte[] getData() {
        return data;
    }

    @Override public void encode(ByteBuf buffer) {
        if (data.length <= 120) {
            buffer.writeByte((byte) data.length);
        } else if (data.length <= 255) {
            buffer.writeByte((byte) 121);
            buffer.writeByte((byte) data.length);
        } else if (data.length <= 65535) {
            buffer.writeByte((byte) 122);
            buffer.writeShort((short) data.length);
        } else {
            buffer.writeByte((byte) 123);
            buffer.writeInt(data.length);
        }
        buffer.writeBytes(data);
    }

    @Override public String toString() {
        return new String(data);
    }
}
