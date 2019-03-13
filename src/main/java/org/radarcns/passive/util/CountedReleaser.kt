/*
 * Copyright 2018 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarcns.passive.util

/**
 * Counted reference. A callback to release is called whenever should release is true, no leases
 * are present and the release has not yet occurred.
 */
class CountedReleaser(private val callback: Callback) {
    private var shouldRelease: Boolean = false
    private var isReleased: Boolean = true
    private var count: Int = 0

    @Synchronized
    fun acquire() {
        count++
        isReleased = false
    }

    fun setShouldRelease(shouldRelease: Boolean) {
        val doRelease: Boolean
        synchronized(this) {
            if (this.shouldRelease == shouldRelease) {
                return
            }
            this.shouldRelease = shouldRelease
            if (shouldRelease && count == 0 && !isReleased) {
                isReleased = true
                doRelease = true
            } else {
                doRelease = false
            }
        }

        if (doRelease) {
            callback.release()
        }
    }

    fun release() {
        val doRelease: Boolean

        synchronized(this) {
            count--
            if (count == 0 && shouldRelease) {
                isReleased = true
                doRelease = true
            } else {
                doRelease = false
            }
        }

        if (doRelease) {
            callback.release()
        }
    }

    interface Callback {
        fun release()
    }
}
