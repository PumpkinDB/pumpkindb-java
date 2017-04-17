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
import java.util.UUID;

public class Uuid implements Encodable {
    private final UUID uuid;

    public Uuid(UUID uuid) {this.uuid = uuid;}

    @Override public void encode(ByteBuf buffer) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        new Data(bb.array()).encode(buffer);
    }
}
