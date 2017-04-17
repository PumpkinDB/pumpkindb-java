/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

public class Bool implements Encodable {
    private final boolean value;

    public Bool(boolean value) {this.value = value;}

    @Override public void encode(ByteBuf buffer) {
        if (value) {
            new Data(new byte[]{1}).encode(buffer);
        } else {
            new Data(new byte[]{0}).encode(buffer);
        }
    }
}
