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
package org.jlibrtp.test;
import java.net.DatagramSocket;

import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;

public class TestRTPSession implements RTPAppIntf {
	public RTPSession rtpSession = null;

	TestRTPSession() {
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;

		try {
			rtpSocket = new DatagramSocket(6002);
			rtcpSocket = new DatagramSocket(6003);
		} catch (Exception e) {
			System.out.println("RTPSession failed to obtain port");
		}


		rtpSession = new RTPSession(rtpSocket, rtcpSocket);

		rtpSession.registerRTPSession(this,null,null);


		Participant p = new Participant("127.0.0.1", 6004, 6005);

		rtpSession.addParticipant(p);
	}


	public void receiveData(DataFrame frame, Participant p) {
		String s = new String(frame.getConcatenatedData());
		System.out.println("The Data has been received: "+s+" , thank you "
				+p.getCNAME()+"("+p.getSSRC()+")");
	}

	public void userEvent(int type, Participant[] participant) {
		//Do nothing
	}

	public int frameSize(int payloadType) {
		return 1;
	}

	public static void main(String[] args) {
		TestRTPSession test = new TestRTPSession();
		//try { Thread.currentThread().sleep(10000); } catch (Exception e) {  };
		long teststart = System.currentTimeMillis();
		String str = "abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd abce abcd ";
		byte[] data = str.getBytes();
		System.out.println(data.length);

		int i=0;
		while(i<100000) {
				test.rtpSession.sendData(data);
				//try { Thread.currentThread().sleep(500); } catch (Exception e) {  };
				i++;
		}

		long testend = System.currentTimeMillis();
		//String str = "efgh";

		//test.rtpSession.sendData(str.getBytes());

		System.out.println("" + (testend - teststart));
	}
}
