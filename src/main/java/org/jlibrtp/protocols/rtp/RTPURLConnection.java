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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;

import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTCPAppIntf;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;


/**
 * <p>Title: RTPURLConnection</p>
 *
 * <p>Description: Handler to protocol rtp:// </p>
 *
 * <p>Copyright: Copyright (c) 2007-2008</p>
 *
 * <p>Company: VoiceInteraction </p>
 *
 * @author Renato Cassaca
 * @version 1.0
 */
public class RTPURLConnection extends URLConnection implements RTPAppIntf,
        RTCPAppIntf {
    // Logger instance.
    private static final Logger LOGGER =
            Logger.getLogger(RTPURLConnection.class.getName());

    //Query parameters of URL
    private HashMap<String, String> parameters;

    //RTP/RTCP sockets
    private DatagramSocket rtpSocket = null;
    private DatagramSocket rtcpSocket = null;

    //RTP/RTCP ports
    private int rtpPort = -1;
    private int rtcpPort = -1;

    //RTP session
    private RTPSession rtpSession = null;

    //Received packets
    private CircularByteBufferAdapter receivedPktsBuffer = null;
    private boolean receivingData = false;
    private int pktsReceivedCount = 0;

    //Send packets
    private boolean sendingData = false;

    //Packets-per-seconds that should be sent
    private int pps = 50;

    //AudioFormat that wil transmitted
    private final AudioFormat audioFormat;

    private String uuid;
    private String _uuid;

    private final boolean keepAlive;

    /**
     * Object constructor
     *
     * @param url URL
     */
    public RTPURLConnection(URL url) throws UnsupportedOperationException,
            URISyntaxException {
        super(url);

        _uuid = UUID.randomUUID().toString();
        uuid = _uuid;
        uuid += "__";
        uuid += url.toExternalForm();

        //Initialize the address of connection
        //host = url.getHost();
        rtpPort = url.getPort();
        if (rtpPort != -1)
            rtcpPort = rtpPort + 1;

        //Get matching URI to extract query parameters
        URI uri = url.toURI();
        parameters = new HashMap<String, String>();
        if (uri.getQuery() != null) {
            String[] parametersString = uri.getQuery().split("\\&");
            for (String part : parametersString) {
                String[] queryElement = part.split("\\=");
                parameters.put(queryElement[0], queryElement[1]);
            }
        }

        //Initialize audio format
        audioFormat = getAudioFormat();

        //Initialize PacketsPerSecond
        String ppsStr = parameters.get("pps");
        if (ppsStr != null) {
            pps = Integer.valueOf(ppsStr);
        }

        //Get keep alive value
        String keepAliveStr = parameters.get("keepAlive");
        if (keepAliveStr != null) {
            keepAlive = Boolean.valueOf(keepAliveStr);
        } else {
            keepAlive = false;
        }

    }

    /**
     * Opens a communications link to the resource referenced by this URL,
     * if such a connection has not already been established.
     *
     * @throws IOException if an I/O error occurs while opening the connection.
     */
    public synchronized void connect() throws IOException {
        if (!connected) {
            //Initialize RTP socket
            try {
                rtpSocket = new DatagramSocket(rtpPort);
            } catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }

            //Initialize RTCP socket
            if (rtpPort != -1) {
                try {
                    rtcpSocket = new DatagramSocket(rtcpPort);
                } catch (Exception ex) {
                    throw new IOException(ex.getMessage());
                }
            }

            //Initialize session
            rtpSession = new RTPSession(rtpSocket, rtcpSocket);
            rtpSession.registerRTPSession(this, null, null);
            rtpSession.sessionBandwidth(getAudioFormatBytesPerSecond());
            rtpSession.packetBufferBehavior(0);
            rtpSession.frameReconstruction(true);
            rtpSession.payloadType(getPayloadType());

            //Marks this URLConnection as connected
            connected = true;
        }
    }


    public InputStream getInputStream() throws IOException {

        if (!connected)
            throw new IOException("Not connected!");
        if (receivingData)
            throw new IOException("Already configured inputStream");

        //Create pipe to make RTP data available
        receivedPktsBuffer = new CircularByteBufferAdapter(
                getAudioFormatBytesPerSecond() * 40);

        receivingData = true;

        //Configure RTPSession
        rtpSession.naivePktReception(true);

        return receivedPktsBuffer.getInputStream();
    }

    /**
     * Returns an output stream that writes to this connection.
     *
     * @return OutputStream
     */
    public OutputStream getOutputStream() throws IOException {

        if (!connected)
            throw new IOException("Not connected!");
        if (sendingData)
            throw new IOException("Already configured outputStream");

        //Configure RTPSession participants
        final String participant = parameters.get("participant");
        if (participant == null) {
            throw new IOException("No participant defined in URL");
        } else {
            final int splitOffset = participant.indexOf(':');
            if (splitOffset == -1) {
                throw new IOException("Invalid participant specified");
            }
            final String partHost = participant.substring(0, splitOffset);
            final String partPort = participant.substring(splitOffset + 1);
            int partRtpPort = 0;
            try {
                partRtpPort = Integer.parseInt(partPort);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid participant specified (port)");
            }

            //Create participant
            final Participant p = new Participant(partHost, partRtpPort,
                                                  partRtpPort + 1);
            rtpSession.addParticipant(p);
        }

        sendingData = true;

        //Builds an OutputStream for this RTPSession
        final RTPOutputStream rtpOS = new RTPOutputStream(rtpSession,
                getAudioFormatBytesPerSecond(), pps);

        return rtpOS;
    }

    /**
     * TODO PipeOutputStream.write blocks until data is written! work around it
     * @param frame DataFrame
     * @param p Participant
     */
    public void receiveData(DataFrame frame, Participant p) {
        if (receivingData) {
            byte[] data = frame.getConcatenatedData();
            try {
                receivedPktsBuffer.getOutputStream().write(data, 0, data.length);
            } catch (IOException ex) {
                if (keepAlive == false) {
                    receivingData = false;
                    /** @todo Should the session be really closed? Won't I check for keepAlive?? */
                    rtpSession.endSession();
                }
            }
        } else {
            //ReceiveData: ignored frame
        }
        pktsReceivedCount++;
    }

    public void userEvent(int type, Participant[] participant) {
        switch (type) {
        case 1:
            proccessBYE();
            break;
        default:
            break;
        }
    }

    public int frameSize(int payloadType) {
        return 1;
    }

    private void proccessBYE() {
        if (keepAlive == true) {
            return;
        }

        try {
            receivedPktsBuffer.getOutputStream().flush();
            int available = 0;
            int previouslyAvailable = -1;
            int counter = 0;

            do {
                available = receivedPktsBuffer.getInputStream().available();
                if (available == 0)
                    break;
                else if (previouslyAvailable < 0) {
                    previouslyAvailable = available;
                    counter = 0;
                } else if (available == previouslyAvailable) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex1) {
                    }
                    counter += 1;
                    if (counter == 50) {
                        LOGGER.warning("Stream wasn't consumed until the end "
                                + uuid);
                        break;
                    }
                } else if (available < previouslyAvailable) {
                    //keep on
                    counter = 0;
                    previouslyAvailable = available;
                } else if (available > previouslyAvailable) {
                    counter = 0;
                    LOGGER.warning("av > pA: How is it possible?? " +
                                       uuid);
                    break;
                }
            } while (available > 0);

            //Clears all accumulated data
            receivedPktsBuffer.getInputStream().skip(receivedPktsBuffer.
                    getInputStream().available());

            if (keepAlive == false) {
                rtpSession.endSession();
                receivedPktsBuffer.getInputStream().close();
                receivedPktsBuffer.getOutputStream().close();
                //System.out.println("Will end RTP session @ proccessBYE " + uuid);

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Given URI parameters, constructs an AudioFormat
     *
     * @return AudioFormat
     */
    private AudioFormat getAudioFormat() {

        //Default values for AudioFormat parameters
        AudioFormat.Encoding encoding = AudioFormat.Encoding.ULAW;
        float sampleRate = 8000;
        int bits = 8;
        int channels = 1;
        boolean endian = true;
        boolean signed = true;

        //Change default values as specified
        String signedStr = parameters.get("signed");
        if (signedStr != null) {
            signed = Boolean.valueOf(signedStr);
        }

        String encodingStr = parameters.get("encoding");
        if (encodingStr != null) {
            if (encodingStr.equals("pcm")) {
                encoding = (signed == true ? AudioFormat.Encoding.PCM_SIGNED :
                            AudioFormat.Encoding.PCM_UNSIGNED);
            } else if (encodingStr.equals("alaw")) {
                encoding = AudioFormat.Encoding.ALAW;
            } else if (encodingStr.equals("ulaw")) {
                encoding = AudioFormat.Encoding.ULAW;
            } else if (encodingStr.equals("gsm")) {
                /** @todo GSM not supported by AudioFormat */
                LOGGER.warning("GSM not supported by AudioFormat... review");
            }
        }

        String rateStr = parameters.get("rate");
        if (rateStr != null) {
            sampleRate = Float.valueOf(rateStr);
        }

        String bitsStr = parameters.get("bits");
        if (bitsStr != null) {
            bits = Integer.valueOf(bitsStr);
        }

        String channelsStr = parameters.get("channels");
        if (channelsStr != null) {
            channels = Integer.valueOf(channelsStr);
        }

        String endianStr = parameters.get("endian");
        if (endianStr != null) {
            if (endianStr.equals("little")) {
                endian = false;
            } else if (endianStr.equals("big")) {
                endian = true;
            }
        }

        //Constructs the AudioFormat
        final AudioFormat audioFormat = new AudioFormat(encoding,
                sampleRate,
                bits,
                channels,
                bits / 8,
                sampleRate,
                endian);

        return audioFormat;
    }


    private int getAudioFormatBytesPerSecond() {
        int bps = audioFormat.getChannels();
        bps *= audioFormat.getSampleRate();
        bps *= (audioFormat.getSampleSizeInBits() / 8);
        return bps;
    }

    /**
     * See {@link http://www.ietf.org/rfc/rfc3551.txt} section 6
     *
     * @return long Pakload type for this session
     */
    private int getPayloadType() {
        if (audioFormat.getEncoding() == AudioFormat.Encoding.ULAW) {
            return 0;
        } else if (audioFormat.getEncoding() == AudioFormat.Encoding.ALAW) {
            return 8;
        } else if (audioFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            if (audioFormat.getSampleSizeInBits() == 16) {
                if (audioFormat.getChannels() == 2) {
                    return 10;
                } else if (audioFormat.getChannels() == 1) {
                    return 11;
                }
            }
        } else if (audioFormat.getEncoding() ==
                   AudioFormat.Encoding.PCM_UNSIGNED) {
            if (audioFormat.getSampleSizeInBits() == 8) {
                //dyn....
                throw new RuntimeException("Dynamic payload type...");
            }
        } else {
            throw new RuntimeException(
                    "Unknown audio format. Cannot guess payload type");
        }
        return 1;
    }

    public void SRPktReceived(long ssrc, long ntpHighOrder, long ntpLowOrder,
                              long rtpTimestamp, long packetCount,
                              long octetCount, long[] reporteeSsrc,
                              int[] lossFraction, int[] cumulPacketsLost,
                              long[] extHighSeq, long[] interArrivalJitter,
                              long[] lastSRTimeStamp, long[] delayLastSR) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("SRPktReceived");
        }
    }

    public void RRPktReceived(long reporterSsrc, long[] reporteeSsrc,
                              int[] lossFraction, int[] cumulPacketsLost,
                              long[] extHighSeq, long[] interArrivalJitter,
                              long[] lastSRTimeStamp, long[] delayLastSR) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("RRPktReceived");
        }
    }

    public void SDESPktReceived(Participant[] relevantParticipants) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("SDESPktReceived");
        }
    }

    public void BYEPktReceived(Participant[] relevantParticipants,
                               String reason) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("BYEPktReceived");
        }
    }

    public void APPPktReceived(Participant part, int subtype, byte[] name,
                               byte[] data) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("APPPktReceived");
        }
    }


    private class CircularByteBufferAdapter extends CircularByteBuffer {
        InputStreamAdapter inputStreamAdapter;
        OutputStreamAdapter outputStreamAdapter;

        boolean stopRead = false;


        public CircularByteBufferAdapter(int size) {
            super(size); //Can be improved!!!
            outputStreamAdapter = new OutputStreamAdapter(super.getOutputStream());
            inputStreamAdapter = new InputStreamAdapter(super.getInputStream(),
                    outputStreamAdapter);
        }

        public InputStream getInputStream() {
            return inputStreamAdapter;
        }

        public OutputStream getOutputStream() {
            return outputStreamAdapter;
        }


        private class InputStreamAdapter extends InputStream {
            private boolean calledClosed = false;
            InputStream inputStream;
            OutputStreamAdapter outputStreamAdapter;
            Object closeLock = new Object();

            public InputStreamAdapter(InputStream is, OutputStreamAdapter osa) {
                this.inputStream = is;
                outputStreamAdapter = osa;
            }

            public int read() throws IOException {
                synchronized (closeLock) {
                    if (stopRead)
                        return -1;
                    else
                        return inputStream.read();
                }
            }

            public int read(byte b[], int off, int len) throws IOException {
                synchronized (closeLock) {
                    if (stopRead)
                        return -1;
                    else {
                        return inputStream.read(b, off, len);
                    }
                }
            }

            public int read(byte b[]) throws IOException {
                return read(b, 0, b.length);
            }


            public void close() throws IOException {
                if (calledClosed) {
                    return;
                }
                calledClosed = true;

                rtpSession.endSession(); //Guarantees that the session is ended

                drain(); //Empties the buffer

                outputStreamAdapter.close();

                synchronized (closeLock) {
                    stopRead = true;
                    inputStream.close();
                }
            }

            private void drain() throws IOException {
                int nowAval = 0;
                int prevAval = -1;
                int eqlCnt = 0;
                do {
                    nowAval = inputStream.available();
                    if (nowAval < 1)
                        break;
                    if (prevAval < 0)
                        prevAval = nowAval;
                    else {
                        if (nowAval > prevAval) {
                            LOGGER.warning("RTPURL.close(): still growing " +
                                    uuid);
                            LOGGER.warning(
                                    "\n------------> DRAIN, RTPURL.close(): still growing!!! " +
                                    uuid + "\n");
                            eqlCnt = 0;
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ex1) {
                                return;
                            }
                        } else if (nowAval == prevAval) {
                            eqlCnt += 1;
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ex1) {
                                return;
                            }

                            if (eqlCnt > 10) {
                                LOGGER.severe(
                                        "RTPURL.close(): bailing out after nRetries aval:" +
                                        nowAval + " " +
                                        uuid);
                                LOGGER.severe(
                                        "\n------------> DRAIN, RTPURL.close(): bailing out after nRetries aval: " +
                                        nowAval + "  " +
                                        uuid + "\n");
                                break;
                            }

                        } else {
                            eqlCnt = 0;
                            try {
                                Thread.sleep(2);
                            } catch (InterruptedException ex) {
                                return;
                            }
                        }
                        prevAval = nowAval;
                    }

                } while (true);
            }

            public int available() throws IOException {
                return inputStream.available();
            }
        }


        private class OutputStreamAdapter extends OutputStream {
            OutputStream outputStream;
            //boolean stopWrite=false;

            public OutputStreamAdapter(OutputStream os) {
                outputStream = os;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void write(byte[] b) throws IOException {
                outputStream.write(b);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }


            public void close() throws IOException {
                receivingData = false;
                //rtpSession.endSession();
                //System.out.println("Will end RTP session @ OutputStreamAdapter.close(), " + uuid);
                outputStream.close();
            }

            public void flush() throws IOException {
                //stopWrite = true;
                outputStream.flush();
            }
        }


    }
}
