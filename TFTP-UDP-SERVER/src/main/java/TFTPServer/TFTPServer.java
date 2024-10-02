package TFTPServer;

import java.io.*;
import java.net.*;

/**
 * TFTP Server -
 * Receives request packets from clients and passes them to TFTPServerThread
 * which responds to read and write requests (RRQ/WRQ)
 * Note that it only supports octet mode and uses port number 1234 rather than
 * to avoid issues of administrator's rights.
 * @author 246644
 * @version 2023
 */
public class TFTPServer {
    /**
     * Main program that runs the network server
     * @param args
     * @throws IOException
     */
    public static void main (String[] args) throws IOException {
        int TFTP_PORT = 1234;
        DatagramSocket serverSocket = null;
        try {
            serverSocket = new DatagramSocket(TFTP_PORT);
        } catch (SocketException e) {
            System.err.println("Could not bind to port, may already be in use.");
            System.exit(1);
        }
        // packet size = opcode (2 bytes) + block number (2 bytes) + data (512 bytes)
        byte[] buffer = new byte[516];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        System.out.println("Starting server...");

        while (true) {
            // wait for incoming packets from clients
            if (serverSocket != null) {
                serverSocket.receive(receivePacket);

                int[] opcode = {receivePacket.getData()[0], receivePacket.getData()[1]};
                // if valid request opcode create a thread
                if (opcode[0] == 0 && (opcode[1] == 1 || opcode[1] == 2)) {
                    System.out.println("\nAccepted UDP packet from " + receivePacket.getAddress() + ", " + receivePacket.getPort());
                    // start new thread to process received packet
                    new TFTPServerThread(receivePacket).start();
                } else {
                    System.out.println("Incorrect data.");
                }
            }
        }
    }
}

