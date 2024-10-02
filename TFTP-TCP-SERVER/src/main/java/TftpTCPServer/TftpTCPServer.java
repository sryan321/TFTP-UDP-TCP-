package TftpTCPServer;

import com.sun.security.ntlm.Server;

import java.net.*;
import java.io.*;

/**
 * TFTP TCP Server - implements a TFTP server built on top of TCP
 * Accepts incoming read and write requests from clients
 * Sends or receives files to/from client and in the latter case, writes them to file on the server
 */
public class TftpTCPServer {

    public static void main(String[] args) throws IOException {

        int portNumber = 10000;
        ServerSocket masterSocket = new ServerSocket(portNumber);
        Socket slaveSocket;

        System.out.println("Starting server...");

        while (true) {
            slaveSocket = masterSocket.accept();
            System.out.println("\nAccepted TCP connection from: " +
                    slaveSocket.getInetAddress() + ", " + slaveSocket.getPort());
            new ClientHandler(slaveSocket).start();
        }
    }
}