/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.base.crypto;

import androidx.annotation.NonNull;

public class SymmetricEncryptionResult {
	private final @NonNull byte[] data;
	private final @NonNull byte[] key;

	public SymmetricEncryptionResult(@NonNull byte[] data, @NonNull byte[] key) {
		this.data = data;
		this.key = key;
	}

	public boolean isEmpty() {
		return data.length == 0;
	}

	@NonNull
	public byte[] getData() {
		return data;
	}

	@NonNull
	public byte[] getKey() {
		return key;
	}
}
