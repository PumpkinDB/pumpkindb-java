/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

import java.util.Iterator;

public class BinaryParsingIterator implements Iterator<Encodable> {

    private final ByteBuf buffer;

    public BinaryParsingIterator(ByteBuf buffer) {this.buffer = buffer;}

    @Override public boolean hasNext() {
        return buffer.isReadable();
    }

    @Override public Encodable next() {
        byte tag = buffer.readByte();
        if (tag <= 120) {
            byte[] b = new byte[tag];
            buffer.readBytes(b);
            return new Data(b);
        }
        if (tag == 121) {
            byte len = buffer.readByte();
            byte[] b = new byte[len];
            buffer.readBytes(b);
            return new Data(b);
        }
        if (tag == 122) {
            short len = buffer.readShort();
            byte[] b = new byte[len];
            buffer.readBytes(b);
            return new Data(b);
        }
        if (tag == 123) {
            int len = buffer.readInt();
            byte[] b = new byte[len];
            buffer.readBytes(b);
            return new Data(b);
        }
        if (tag > (byte)128) {
            int len = (int)tag - 128;
            byte[] b = new byte[len];
            buffer.readBytes(b);
            return new Instruction(new String(b));
        }
        throw new IllegalArgumentException();
    }
}
