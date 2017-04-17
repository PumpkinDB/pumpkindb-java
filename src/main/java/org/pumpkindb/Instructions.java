/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public class Instructions implements Encodable {
    private final String instructions;

    public Instructions(String instructions) {this.instructions = instructions;}

    @Override public void encode(ByteBuf buffer) {
        Arrays.stream(instructions.split("(\\s|\\n)"))
              .map(Instruction::new)
              .forEachOrdered(i -> i.encode(buffer));
    }
}
