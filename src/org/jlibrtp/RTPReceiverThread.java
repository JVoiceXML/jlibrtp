/**
 * Java RTP Library (jlibrtp)
 * Copyright (C) 2006 Arne Kepp
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
package org.jlibrtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The RTP receiver thread waits on the designated UDP socket for new packets.
 *
 * Once one arrives, it is parsed and tested. We also check the ip-address of the sender.
 * If accepted, the packet is added onto the packet buffer of the participant.
 *
 * A separate thread moves the packet from the packet buffer to the application.
 *
 * @author Arne Kepp
 */
public class RTPReceiverThread extends Thread {
    /** Logger instance. */
    private static final Logger LOGGER =
        Logger.getLogger(RTPReceiverThread.class.getName());

    /** Parent RTP Session */
    private RTPSession rtpSession = null;

    public RTPReceiverThread(RTPSession session) {
        rtpSession = session;
        if(LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("<-> RTPReceiverThread created");
        }
        setName("RTPReceiverThread");
    }

    public void run() {
        if(LOGGER.isLoggable(Level.FINEST)) {
            if(rtpSession.mcSession) {
                LOGGER.finest("-> RTPReceiverThread.run() starting on MC " + rtpSession.rtpMCSock.getLocalPort() );
            } else {
                LOGGER.finest("-> RTPReceiverThread.run() starting on " + rtpSession.rtpSock.getLocalPort() );
            }
        }

        while(!rtpSession.endSession) {
            if(LOGGER.isLoggable(Level.FINEST)) {
                if(rtpSession.mcSession) {
                    LOGGER.finest("-> RTPReceiverThread.run() waiting for MC packet on " + rtpSession.rtpMCSock.getLocalPort() );
                } else {
                    LOGGER.finest("-> RTPReceiverThread.run() waiting for packet on " + rtpSession.rtpSock.getLocalPort() );
                }
            }

            // Prepare a packet
            byte[] rawPkt = new byte[1500];
            DatagramPacket packet = new DatagramPacket(rawPkt, rawPkt.length);
            // Wait for it to arrive
            if(! rtpSession.mcSession) {
                //Unicast
                try {
                    rtpSession.rtpSock.receive(packet);
                } catch (IOException e) {
                    if(!rtpSession.endSession) {
                        e.printStackTrace();
                    } else {
                        continue;
                    }
                }
            } else {
                //Multicast
                try {
                    rtpSession.rtpMCSock.receive(packet);
                } catch (IOException e) {
                    if(!rtpSession.endSession) {
                        e.printStackTrace();
                    } else {
                        continue;
                    }
                }
            }

            // Parse the received RTP (?) packet
            RtpPkt pkt = new RtpPkt(rawPkt, packet.getLength());
            if(pkt.getVersion() != 2) {
                // Some old phones, like the SNOM 190, are transmitting one
                // Version=0 RTP packet before sending Version=2 RTP packets.
                System.out.println("Received invalid RTP packet. Ignoring");
                continue;
            }
            long pktSsrc = pkt.getSsrc();

            // Check for loops and SSRC collisions
            if( rtpSession.ssrc == pktSsrc )
                rtpSession.resolveSsrcConflict();

            long[] csrcArray = pkt.getCsrcArray();
            if( csrcArray != null) {
                for(int i=0; i< csrcArray.length; i++) {
                    if(csrcArray[i] == rtpSession.ssrc) {
                        rtpSession.resolveSsrcConflict();
                    }
                }
            }

            if(LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("-> RTPReceiverThread.run() rcvd packet, seqNum " + rtpSession.seqNum + ", ssrc " + pktSsrc);
                String str = new String(pkt.getPayload());
                LOGGER.finest("-> RTPReceiverThread.run() payload is " + str );
            }

            //Find the participant in the database based on SSRC
            Participant part = rtpSession.partDb.getParticipant(pktSsrc);

            if(part == null) {
                InetSocketAddress nullSocket = null;
                part = new Participant((InetSocketAddress) packet.getSocketAddress(), nullSocket, pkt.getSsrc());
                part.unexpected = true;
                rtpSession.partDb.addParticipant(1, part);
                // Note: when adding participant, the result may be an updated pre-existing participant that lacked
                // a ssrc. Thus, we need to find the participant that is really stored in the partDb after adding.
                // Otherwise the packet will be added to a participant not connected to a rtpSession.
                part = rtpSession.partDb.getParticipant(pktSsrc);
            }

            // Do checks on whether the datagram came from the expected source for that SSRC.
            if(part.rtpAddress == null || packet.getAddress().equals(part.rtpAddress.getAddress())) {
                PktBuffer pktBuffer = part.pktBuffer;

                if(pktBuffer != null) {
                    //A buffer already exists, append to it
                    pktBuffer.addPkt(pkt);
                } else {
                    // Create a new packet/frame buffer
                    pktBuffer = new PktBuffer(this.rtpSession, part,pkt);
                    part.pktBuffer = pktBuffer;
                }
            } else {
                LOGGER.warning("RTPReceiverThread: Got an unexpected packet from " + pkt.getSsrc()
                        + " the sending ip-address was " + packet.getAddress().toString()
                        + ", we expected from " + part.rtpAddress.toString());
            }

            // Statistics for receiver report.
            part.updateRRStats(packet.getLength(), pkt);
            // Upate liveness
            part.lastRtpPkt = System.currentTimeMillis();

            if(LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("<-> RTPReceiverThread signalling pktBufDataReady");
            }

            // Signal the thread that pushes data to application
            rtpSession.pktBufLock.lock();
            try { rtpSession.pktBufDataReady.signalAll(); } finally {
                rtpSession.pktBufLock.unlock();
            }

        }
    }

}
