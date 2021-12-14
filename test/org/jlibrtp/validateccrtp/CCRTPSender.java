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
package org.jlibrtp.validateccrtp;

import java.net.DatagramSocket;

import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;

/**
 * Sends packet to the rtplisten demo program in ccrtp 1.5.x
 *
 * Listen on port 6004, unless you modify this program.
 *
 * @author Arne Kepp
 *
 */

public class CCRTPSender implements RTPAppIntf {
	RTPSession rtpSession = null;

	public CCRTPSender() {
		// Do nothing;
	}

	public void receiveData(DataFrame frame, Participant p) {
		System.out.println("Got data: " + new String(frame.getConcatenatedData()));
	}

	public void userEvent(int type, Participant[] participant) {
		//Do nothing
	}

	public int frameSize(int payloadType) {
		return 1;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CCRTPSender me = new CCRTPSender();

		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;

		try {
			rtpSocket = new DatagramSocket(16384);
			rtcpSocket = new DatagramSocket(16385);
		} catch (Exception e) {
			System.out.println("RTPSession failed to obtain port");
		}

		me.rtpSession = new RTPSession(rtpSocket, rtcpSocket);
		me.rtpSession.naivePktReception(true);
		me.rtpSession.registerRTPSession(me,null,null);

		Participant p = new Participant("127.0.0.1", 16386, 16387);
		me.rtpSession.addParticipant(p);

		//me.rtpSession.setPayloadType(0);

		for(int i=0; i<10; i++) {
			String str = "Test number " + i;
			me.rtpSession.sendData(str.getBytes());
		}
	}

}
