package com.github.plusvic.yara.embedded;

import com.github.plusvic.yara.*;
import org.fusesource.hawtjni.runtime.Callback;

import java.io.File;
import java.io.IOException;

import static com.github.plusvic.yara.Preconditions.checkArgument;
import static com.github.plusvic.yara.Preconditions.checkState;

/**
 * User: pba
 * Date: 6/7/15
 * Time: 10:06 AM
 */
public class YaraScannerImpl implements YaraScanner {
    private static final long CALLBACK_MSG_RULE_MATCHING = 1;

    private class NativeScanCallback {
        private final YaraLibrary library;
        private final YaraScanCallback callback;

        public NativeScanCallback(YaraLibrary library, YaraScanCallback callback) {
            this.library = library;
            this.callback = callback;
        }

        long nativeOnScan(long type, long message, long data) {
            if (type == CALLBACK_MSG_RULE_MATCHING) {
                YaraRuleImpl rule = new YaraRuleImpl(library, message);
                callback.onMatch(rule);
            }
            return 0;
        }
    }

    private YaraLibrary library;
    private Callback callback;
    private long peer;
    private int timeout = 60;

    YaraScannerImpl(YaraLibrary library, long rules) {
        checkArgument(library != null);
        checkArgument(rules != 0);

        this.library = library;
        this.peer = rules;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    @Override
    public void close() throws IOException {
        if (callback != null) {
            callback.dispose();
            callback = null;
        }

        if (peer != 0) {
            library.rulesDestroy(peer);
            peer = 0;
        }
        library = null;
    }

    /**
     * Set scan timeout
     */
    public void setTimeout(int timeout) {
        checkArgument(timeout >= 0);
        this.timeout = timeout;
    }

    /**
     * Set scan callback
     *
     * @param cbk
     */
    public void setCallback(YaraScanCallback cbk) {
        checkArgument(cbk != null);

        if (callback != null) {
            callback.dispose();
            callback = null;
        }

        callback = new Callback(new NativeScanCallback(library, cbk), "nativeOnScan", 3);
    }

    /**
     * Scan file
     *
     * @param file
     */
    public void scan(File file) {
        checkState(callback != null);

        int ret = library.rulesScanFile(peer, file.getAbsolutePath(), 0, callback.getAddress(), 0, timeout);
        if (!ErrorCode.isSuccess(ret)) {
            throw new YaraException(ret);
        }
    }
}
