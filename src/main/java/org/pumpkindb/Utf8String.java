/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

public class Utf8String extends Data {

    public Utf8String(String data) {
        super(data.getBytes());
    }

    public String getValue() {
        return new String(getData());
    }
}
