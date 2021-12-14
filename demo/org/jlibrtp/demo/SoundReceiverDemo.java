/* This file is based on
 * http://www.anyexample.com/programming/java/java_play_wav_sound_file.xml
 * 
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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.jlibrtp.DataFrame;
import org.jlibrtp.Participant;
import org.jlibrtp.RTPAppIntf;
import org.jlibrtp.RTPSession;

/**
 * @author Arne Kepp
 */
public class SoundReceiverDemo implements RTPAppIntf {
	//test
	RTPSession rtpSession = null;
	private Position curPosition;
	byte[] abData = null;
	int nBytesRead = 0;
	int pktCount = 0;
	int dataCount = 0;
	int offsetCount = 0;
	SourceDataLine auline;

	 enum Position {
		LEFT, RIGHT, NORMAL
	};

	public void receiveData(DataFrame frame, Participant p) {
		if(auline != null) {
			byte[] data = frame.getConcatenatedData();
			auline.write(data, 0, data.length);

			//dataCount += data.length;
			//if(pktCount % 10 == 0) {
			//	System.out.println("pktCount:" + pktCount + " dataCount:" + dataCount);
			//	long test = 0;
			//	for(int i=0; i<data.length; i++) {
			//		test += data[i];
			//	}
			//	System.out.println(Long.toString(test));
			//}
		}
		pktCount++;
	}

	public void userEvent(int type, Participant[] participant) {
		//Do nothing
	}
	public int frameSize(int payloadType) {
		return 1;
	}

	public SoundReceiverDemo(int rtpPort, int rtcpPort)  {
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;

		try {
			rtpSocket = new DatagramSocket(rtpPort);
			rtcpSocket = new DatagramSocket(rtcpPort);
		} catch (Exception e) {
			System.out.println("RTPSession failed to obtain port");
		}


		rtpSession = new RTPSession(rtpSocket, rtcpSocket);
		rtpSession.naivePktReception(true);
		rtpSession.registerRTPSession(this,null, null);

		//Participant p = new Participant("127.0.0.1", 6001, 6002);
		//rtpSession.addParticipant(p);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Setup");

		if(args.length == 0) {
			System.out.println("Syntax:");
			System.out.println("java SoundReceiverDemo <rtpPort> <rtcpPort>");
			System.out.println("Assuming 16384 and 16385 for testing purposes");

			args = new String[2];
			args[0] = new String("16384");
			args[1] = new String("16385");
		}



		//SoundReceiverDemo aDemo = new SoundReceiverDemo(
		//		Integer.getInteger(args[0]), Integer.getInteger(args[1]));
		SoundReceiverDemo aDemo = new SoundReceiverDemo( 16384, 16385);

		aDemo.doStuff();
		System.out.println("Done");
	}

	public void doStuff() {
		System.out.println("-> ReceiverDemo.doStuff()");
		AudioFormat.Encoding encoding =  new AudioFormat.Encoding("PCM_SIGNED");
		AudioFormat format = new AudioFormat(encoding,((float) 8000.0), 16, 1, 2, ((float) 8000.0) ,false);
		System.out.println(format.toString());
		auline = null;
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

		try {
			auline = (SourceDataLine) AudioSystem.getLine(info);
			auline.open(format);
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			return;
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (auline.isControlSupported(FloatControl.Type.PAN)) {
			FloatControl pan = (FloatControl) auline
					.getControl(FloatControl.Type.PAN);
			if (this.curPosition == Position.RIGHT)
				pan.setValue(1.0f);
			else if (this.curPosition == Position.LEFT)
				pan.setValue(-1.0f);
		}

		auline.start();
		try {
			while (nBytesRead != -1) {
				// Used to write audio to auline here,
				// now moved directly to receiveData.
				try { Thread.sleep(1000); } catch(Exception e) { }
			}
		} finally {
			auline.drain();
			auline.close();
		}
	}
}
