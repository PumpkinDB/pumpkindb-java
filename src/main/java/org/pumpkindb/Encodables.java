/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

public class Encodables implements Encodable {

    private final Iterable<Encodable> encodables;

    public Encodables(Iterable<Encodable> encodables) {this.encodables = encodables;}

    @Override public void encode(ByteBuf buffer) {
        encodables.forEach(encodable -> encodable.encode(buffer));
    }

}
