/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.Arrays;

public class Closure implements Encodable {

    private final Iterable<Encodable> encodables;

    public Closure(Iterable<Encodable> encodables) {
        this.encodables = encodables;
    }

    public Closure(Encodable... encondables) {
        this(new ArrayList<>(Arrays.asList(encondables)));
    }

    @Override public void encode(ByteBuf buffer) {
        ByteBuf buf = Unpooled.buffer();
        encodables.forEach(encodable -> encodable.encode(buf));

        int index = buf.writerIndex();

        buf.capacity(index);

        new Data(buf.array()).encode(buffer);
    }
}
