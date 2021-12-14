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

import java.io.File;
import java.net.DatagramSocket;
import java.util.Iterator;
import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;
import org.jlibrtp.StaticProcs;

public class XmlPacketPlayer implements RTPAppIntf {
	RTPSession rtpSession = null;
	Document document = null;
	long origStartTime = -1;
	long startTime = -1;
	int dataCount = 0;
	int pktCount = 0;

	/**
	 * Constructor
	 */
	public XmlPacketPlayer(int rtpPortNum, int rtcpPortNum, String address) {
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;

		try {
			rtpSocket = new DatagramSocket(rtpPortNum+2);
			rtcpSocket = new DatagramSocket(rtcpPortNum+2);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println("RTPSession failed to obtain port");
		}

		this.rtpSession = new RTPSession(rtpSocket, rtcpSocket);
		Participant p = new Participant(address, rtpPortNum, rtcpPortNum);
		this.rtpSession.addParticipant(p);

		System.out.println("Done creating player.");
	}

	public void parseDocument(String filename) {
		System.out.println("Parsing document " + filename);
		try {
			SAXBuilder builder = new SAXBuilder();
			this.document = builder.build(new File(filename));
		} catch(Exception e) {
			e.printStackTrace();
		}

		//RTPSession
		Element elm = document.getRootElement();
		List<Element> children = elm.getChildren();

		Iterator<Element> iter = children.iterator();

		//sessionInformation
		elm = iter.next();

		parseSessionInfo(elm);

		//this.rtpSession.RTPSessionRegister(this,null,null);

		// RTP and RTCP packets
		while(iter.hasNext()) {
			elm = (Element) iter.next();
			if(elm.getName().equals("RTPpacket")) {
				parseRTPpacket(elm);
			} else {
				//System.out.println("ah..." + elm.getName());
			}
		}

	}

	public void parseSessionInfo(Element elm) {
		System.out.println("Parsing Session Information");

		List<Element> children = elm.getChildren();
		Iterator<Element> iter = children.iterator();

		int i = 0;
		while(i < 3 && iter.hasNext()) {
			elm = iter.next();
			if(elm.getName().equals("SSRC")) {
				//Ignore
			} else if(elm.getName().equals("CNAME")) {
				this.rtpSession.CNAME(elm.getValue());
			} else if(elm.getName().equals("sessionStart")) {
				this.origStartTime = Long.parseLong(elm.getValue());
			}
			i++;
		}
		this.startTime = System.currentTimeMillis();
	}

	public void parseRTPpacket(Element elm) {
		//System.out.println("Parsing RTP packet");

		long ssrc = -1;
		long targetTime = -1;
		long rtpTimestamp = -1;
		int seqNum = -1;
		int payloadType = -1;
		byte[] buf = null;

		List<Element> children = elm.getChildren();
		Iterator<Element> iter = children.iterator();

		while(iter.hasNext()) {
			elm = iter.next();
			if(elm.getName().equals("SSRC")) {
				ssrc = Long.parseLong(elm.getValue());

			} else if(elm.getName().equals("ArrivalTimestamp")) {
				targetTime = Long.parseLong(elm.getValue());

			} else if(elm.getName().equals("RTPTimestamp")) {
				rtpTimestamp = Long.parseLong(elm.getValue());

			} else if(elm.getName().equals("SequenceNumber")) {
				seqNum = Integer.parseInt(elm.getValue());

			} else if(elm.getName().equals("PayloadType")) {
				payloadType = Integer.parseInt(elm.getValue());

			} else if(elm.getName().equals("Payload")) {
				byte[] bytes = elm.getValue().getBytes();
				byte[] tmp = new byte[2];
				buf = new byte[bytes.length/2];
				int ipos = 0;
				int jpos = 0;
				while(ipos < bytes.length) {
					tmp[0] = bytes[ipos++];
					tmp[1] = bytes[ipos++];
					buf[jpos++] = StaticProcs.byteOfHex(tmp);
				}
			}
		}
		rtpSession.payloadType(payloadType);
		preSendSleep(targetTime);
		rtpSession.sendData(buf);

		//dataCount += buf.length;
		//if(pktCount % 10 == 0) {
		//	System.out.println("pktCount:" + pktCount + " dataCount:" + dataCount);
		//
		//	long test = 0;
		//	for(int i=0; i<buf.length; i++) {
		//		test += buf[i];
		//	}
		//	System.out.println(Long.toString(test));
		//}
		pktCount++;

		//System.out.print(".");
	}

	/**
	 * This should be
	 * @param targetTime the real UNIX timestamp of the packet
	 */
	public void preSendSleep(long targetTime) {
		long curDiff = System.currentTimeMillis() - this.startTime;
		long origDiff = targetTime - this.origStartTime;

		long sleepTime = 1;
		if( origDiff > curDiff) {
			sleepTime = origDiff - curDiff;
		}

		//System.out.println("Sleeping for " + sleepTime);

		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) { }
	}

	/**
	 * Dummy methods for the RTPAppinterface
	 */
	public void receiveData(DataFrame frame, Participant participant) {
		//dummy;
	}
	public void userEvent(int type, Participant[] participant) {
		//dummy
	}
	public int frameSize(int payloadType) {
		//dummy
		return 1;
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int rtpPortNum = -1;
		int rtcpPortNum = -1;
		String hostname = "";
		String filename = "";

		boolean run = false;
		if (args.length == 4) {
		    try {
		    	hostname = args[0];
		    	rtpPortNum = Integer.parseInt(args[1]);
		    	rtcpPortNum = Integer.parseInt(args[2]);
		    	filename = args[3];
		    } catch (NumberFormatException e) {
		    	System.out.println(e.getMessage());
		    }
		    run = true;
		} else if(args.length == 0) {
			System.out.println("Syntax: ");
			System.out.println("java XmlPacketPlayer <hostname> <RTP target port> <RTCP target port> <file to read>");
			System.out.println("");
			System.out.println("Using default values for testing, will only work on a UNIX clone:");
			System.out.println("java XmlPacketPlayer 127.0.0.1 16384 16385 /home/ak/jlibrtp_packets.xml");
			hostname = "127.0.0.1";
			rtpPortNum = 16384;
	    	rtcpPortNum = 16385;
	    	filename =  "test.xml";
			run = true;
		} else {
			System.out.println("Syntax: ");
			System.out.println("java XmlPacketPlayer <hostname> <RTP target port> <RTCP target port> <file to read>");
		}

		if(run) {
			XmlPacketPlayer player = new XmlPacketPlayer(rtpPortNum, rtcpPortNum, hostname);
			player.parseDocument(filename);
		}
	}
}
