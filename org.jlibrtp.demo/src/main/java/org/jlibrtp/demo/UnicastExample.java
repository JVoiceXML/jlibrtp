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
package org.jlibrtp.demo;

import java.net.DatagramSocket;

import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;

/**
 * <p>This is an example of how to set up a Unicast session.</p>
 * <p>It does not accept any input arguments and is therefore of limited practical value, but it shows
 * the basics.</p>
 *
 * <p> The class has to implement RTPAppIntf.</p>
 * @author Arne Kepp
 */
public class UnicastExample implements RTPAppIntf {
	/** Holds a RTPSession instance */
	RTPSession rtpSession = null;

	/** A minimal constructor */
	public UnicastExample(RTPSession rtpSession) {
		this.rtpSession = rtpSession;
	}

	// RTPAppIntf  All of the following are documented in the JavaDocs
	/** Used to receive data from the RTP Library. We expect no data */
	public void receiveData(DataFrame frame, Participant p) {
		/**
		 * This concatenates all received packets for a single timestamp
		 * into a single byte[]
		 */
		byte[] data = frame.getConcatenatedData();

		/**
		 * This returns the CNAME, if any, associated with the SSRC
		 * that was provided in the RTP packets received.
		 */
		String cname = p.getCNAME();

		System.out.println("Received data from " + cname);
		System.out.println(new String(data));
	}

	/** Used to communicate updates to the user database through RTCP */
	public void userEvent(int type, Participant[] participant) {
		//Do nothing
	}

	/** How many packets make up a complete frame for the payload type? */
	public int frameSize(int payloadType) {
		return 1;
	}
	// RTPAppIntf/


	public static void main(String[] args) {
		// 1. Create sockets for the RTPSession
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;
		try {
			rtpSocket = new DatagramSocket(16384);
			rtcpSocket = new DatagramSocket(16385);
		} catch (Exception e) {
			System.out.println("RTPSession failed to obtain port");
		}

		// 2. Create the RTP session
		RTPSession rtpSession = new RTPSession(rtpSocket, rtcpSocket);

		// 3. Instantiate the application object
		UnicastExample uex = new UnicastExample(rtpSession);

		// 4. Add participants we want to notify upon registration
		// a. Hopefully nobody is listening on this port.
		Participant part = new Participant("127.0.0.1",16386,16387);
		rtpSession.addParticipant(part);

		// 5. Register the callback interface, this launches RTCP threads too
		// The two null parameters are for the RTCP and debug interfaces, not use here
		rtpSession.registerRTPSession(uex, null, null);

		// Wait 2500 ms, because of the initial RTCP wait
		try{ Thread.sleep(2000); } catch(Exception e) {}

		// Note: The wait is optional, but insures SDES packets
		//       receive participants before continuing

		// 6. Send some data
		String str = "Hi there!";
		rtpSession.sendData(str.getBytes());

		// 7. Terminate the session, takes a few ms to kill threads in order.
		rtpSession.endSession();
		//This may result in "Sleep interrupted" messages, ignore them
	}
}
