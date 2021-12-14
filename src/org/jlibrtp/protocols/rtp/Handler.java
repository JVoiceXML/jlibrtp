/**
 * Java RTP Library (jlibrtp)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jlibrtp.protocols.rtp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.UnknownHostException;
import java.util.logging.Logger;


/**
 * <p>Title: Handler for rtp:// protocol</p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2007-2008</p>
 *
 * <p>Company: VoiceInteraction</p>
 *
 * @author Renato Cassaca
 * @version 1.0
 */
public class Handler extends URLStreamHandler {
    /** Logger instance. */
    private static final Logger LOGGER =
        Logger.getLogger(Handler.class.getName());

    public Handler() {
        super();
    }

    protected URLConnection openConnection(URL url) throws IOException {
        try {
            return new RTPURLConnection(url);
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid provided URL");
        }
    }

    /**
     * Returns the default port for a URL parsed by this handler.
     *
     * @return the default port for a <code>URL</code> parsed by this handler.
     */
    protected int getDefaultPort() {
        return 0;
    }

    /**
     * Get the IP address of our host.
     *
     * @param u a URL object
     * @return an <code>InetAddress</code> representing the host IP address.
     */
    protected synchronized InetAddress getHostAddress(URL u) {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            LOGGER.warning(ex.getMessage());
            return null;
        }
    }

}
