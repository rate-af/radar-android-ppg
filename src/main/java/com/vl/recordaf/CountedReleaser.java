package com.vl.recordaf;

/**
 * Counted reference. A callback to release is called whenever should release is true, no leases
 * are present and the release has not yet occurred.
 */
public class CountedReleaser {
    private final Callback callback;
    private boolean shouldRelease;
    private boolean isReleased;
    private int count;

    public CountedReleaser(Callback callback) {
        this.callback = callback;
        shouldRelease = false;
        isReleased = true;
        count = 0;
    }

    public synchronized void acquire() {
        count++;
        isReleased = false;
    }

    public void setShouldRelease(boolean shouldRelease) {
        boolean doRelease;
        synchronized (this) {
            if (this.shouldRelease == shouldRelease) {
                return;
            }
            this.shouldRelease = shouldRelease;
            if (shouldRelease && count == 0 && !isReleased) {
                isReleased = true;
                doRelease = true;
            } else {
                doRelease = false;
            }
        }

        if (doRelease) {
            callback.release();
        }
    }

    public void release() {
        boolean doRelease;

        synchronized (this) {
            count--;
            if (count == 0 && shouldRelease) {
                isReleased = true;
                doRelease = true;
            } else {
                doRelease = false;
            }
        }

        if (doRelease) {
            callback.release();
        }
    }

    public interface Callback {
        void release();
    }
}
