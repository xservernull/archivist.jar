// wraps the client side of a packet. an exception thrown while a client handles a packet makes
// minecraft drop the connection ("connection lost", plus a debug/disconnect-*-client.txt

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class SafeClient {
    private SafeClient() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static void run(String packet, Runnable handler) {
        try {
            handler.run();
        } catch (Throwable t) {
            LOG.error("[net] client handler for '{}' failed; the connection is kept", packet, t);
        }
    }
}
