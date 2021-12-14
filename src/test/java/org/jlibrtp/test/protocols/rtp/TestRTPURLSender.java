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
package org.jlibrtp.test.protocols.rtp;

import java.net.URL;
import java.net.URLConnection;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;


/**
 * <p>Title: jlibrtp</p>
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
public class TestRTPURLSender {

    static {
        registerProtocolHandlers();
    }

    private static void registerProtocolHandlers() {
        //Register protocol handler
        String javaPropName = "java.protocol.handler.pkgs";

        //Start value
        System.out.println(javaPropName + " = " +
                           System.getProperty(javaPropName));

        //Vou actualizar a propriedade que define o meu protocol handler (URL)
        String packageName = "org.jlibrtp.protocols";
        System.setProperty(javaPropName, packageName);

        //Value after update
        System.out.println(javaPropName + " = " +
                           System.getProperty(javaPropName));
    }


    public TestRTPURLSender() {
        super();
    }

    public static void main(String[] args) {

        TestRTPURLSender testrtpurlsender = new TestRTPURLSender();
        testrtpurlsender.doIt();
    }

    /**
     * doIt
     */
    private void doIt() {
        try {
            // This block configure the logger with handler and formatter
            FileHandler fh = new FileHandler("Sender.log", false);
            Logger logger = Logger.getLogger("org.jlibrtp");
            logger.addHandler(fh);
            logger.setLevel(Level.ALL);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);

            long sTime = System.currentTimeMillis();

            URL sendURL = new URL("rtp://172.16.4.42:29000/audio?participant=localhost:30000&rate=8000");
            URLConnection sendC = sendURL.openConnection();
            sendC.connect();
            OutputStream rtpOS = sendC.getOutputStream();

            FileInputStream fis = new FileInputStream("capture.wav");
            BufferedInputStream bis = new BufferedInputStream(fis);
            AudioInputStream is = AudioSystem.getAudioInputStream(bis);

            byte[] buffer = new byte[1024];
            int br;
            while ((br = is.read(buffer)) != -1) {
                rtpOS.write(buffer, 0, br);
            }
            rtpOS.flush();
            rtpOS.close();

            System.out.println("Finished Sender: " + (System.currentTimeMillis() - sTime) / 1000);


        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
