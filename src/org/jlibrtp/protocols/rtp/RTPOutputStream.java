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
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlibrtp.RTPSession;

/**
 * <p>Title: RTPOutputStream </p>
 *
 * <p>Description: Output stream that sends the audio in "real time"</p>
 *
 * <p>Copyright: Copyright (c) 2008</p>
 *
 * <p>Company: www.VoiceInteraction.pt</p>
 *
 * @author Renato Cassaca
 * @version 1.0
 */
public class RTPOutputStream extends OutputStream {
    /** Logger instance. */
    private static final Logger LOGGER =
        Logger.getLogger(RTPOutputStream.class.getName());

    //RTPSession
    private final RTPSession rtpSession;

    //Number of RTP packets that should be sent per second
    private final long packetSize;

    //The time in seconds of each frame contents
    private final double packetDuration;

    //Buffer that will store bytes to send
    private final CircularByteBuffer circularByteBuffer;

    //The next packet timestamp
    private long pktTmestamp;

    //Buffer that hols temporary read data
    private final byte[] buffer;

    /**
     * Constructor
     * Given a RTPSession builds an OutputStream to it
     *
     * @param rtpSession RTPSession
     * @param bytesPerSecond long
     * @param packetsPerSecond int
     */
    public RTPOutputStream(RTPSession rtpSession, long bytesPerSecond,
                           int packetsPerSecond) {
        this.rtpSession = rtpSession;

        packetSize = bytesPerSecond / packetsPerSecond;
        packetDuration = 1000f * ((double) packetSize / (double) bytesPerSecond);
        pktTmestamp = -1;
        buffer = new byte[(int) packetSize];

        circularByteBuffer = new CircularByteBuffer(buffer.length);
    }

    public void write(int b) throws IOException {
        circularByteBuffer.getOutputStream().write(b);

        drain();
    }

    public void write(byte b[], int off, int len) throws IOException {
        circularByteBuffer.getOutputStream().write(b, off, len);

        drain();
    }

    private void drain() throws IOException {
        while (circularByteBuffer.getInputStream().available() >= packetSize) {
            sendData();
        }
    }

    public void flush() throws IOException {
        drain();

        //Make sure that buffer is empty
        if (circularByteBuffer.getInputStream().available() > 0) {
            circularByteBuffer.clear();
        }

        pktTmestamp = -1;
    }

    public void close() throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RTPOutputStream.close() called");
        }
        circularByteBuffer.getOutputStream().close();
        circularByteBuffer.getInputStream().close();
        rtpSession.endSession();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RTPOutputStream.close() done! (rtpEndSession)");
        }
        pktTmestamp = -1;
    }

    /**
     * Send data to RTP session
     *
     * @todo Should reset timestamp if current time has long passed
     */
    private void sendData() throws IOException {

        //Initialize timestamp
        if (pktTmestamp < 0) {
            pktTmestamp = (long) (System.nanoTime() * 1E-6);
        }

        //Fill buffer to send
        int bytesRead = circularByteBuffer.getInputStream().read(buffer);
        if (bytesRead != packetSize) {
            if (bytesRead < 0) {
                //ENDED??
                LOGGER.info("bytesRead != packetSize... @ RTPOutputStream");
            }
        }

        //Send data
        byte[][] pkt = {buffer};
        rtpSession.sendData(pkt, null, null, pktTmestamp, null);

        //Try to keep send rate as "real time" as possible...
        long sleepTime = pktTmestamp - (long) (System.nanoTime() * 1E-6);
        while (sleepTime > 0) {
            try {
                Thread.sleep(0, 999999);
                sleepTime--;
            } catch (InterruptedException ex) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(ex.getLocalizedMessage());
                }
                return;
            }
        }

        //Update timestamp
        pktTmestamp += packetDuration;
    }

}
