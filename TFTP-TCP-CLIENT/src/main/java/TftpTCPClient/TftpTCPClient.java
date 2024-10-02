package TftpTCPClient;

import java.net.*;
import java.io.*;

/**
 * TFTP TCP Client - implementation of the TFTP on top of TCP for a client
 * Operation request and filename read from client via command line
 * Sends request to server and either reads a file from or writes a file to server
 * @param args[0] address
 * @param args[1] portNumber
 * Usage: java TftpTCPClient <address> <portNumber>
 */
public class TftpTCPClient {
    private static final String OP_RRQ = "01";
    private static final String OP_WRQ = "02";

    public static void main(String[] args) throws IOException {
        Socket clientSocket;
        int portNumber;
        String address;
        String filename;
        String op_code;
        String userRequest;
        // write to socket using send and receive objects below
        DataOutputStream send;
        BufferedReader receive;
        // to get requests, use stdIn
        BufferedReader stdIn;
        // to write/read to/from files use FileReader and BufferedWriter
        BufferedWriter bufferedWriter;
        FileReader fileReader;

        if (args.length != 2) {
            System.err.println("Usage: java TftpTCPClient <address> <port>");
            System.exit(1);
        }
        address = args[0];
        portNumber = Integer.parseInt(args[1]);

        try {
            clientSocket = new Socket(address, portNumber);
            send = new DataOutputStream(clientSocket.getOutputStream());
            receive = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            bufferedWriter = null;
            fileReader = null;
            stdIn = new BufferedReader(new InputStreamReader(System.in));

            // get user request
            op_code = filename = null;
            System.out.println("----OPCODES-------\n----<01> Read-----\n----<02> Write----\n<op_code><filename>\nExample: 01file.txt\nEnter request and filename:");
            if ((userRequest = stdIn.readLine()).compareTo("exit") != 0) {
                op_code = userRequest.substring(0,2);
                filename = userRequest.substring(2);
            }
            if (!op_code.equals(OP_RRQ) && !op_code.equals(OP_WRQ)) {
                System.err.println("Not valid opcodes");
                System.exit(1);
            }
            // add request to send packet to send to server
            send.writeUTF(op_code+filename+"\n");
            System.out.println("Sending request to server...");
            char[] chars = new char[1024];
            // read request
            if (op_code.equals(OP_RRQ)) {
                System.out.println("Requesting data...");
                try {
                    bufferedWriter = new BufferedWriter(new FileWriter(filename));
                    int charsRead;
                    // check to see if data received; if not file may not exist on the server
                    boolean dataReceived = false;
                    // read chars from received packet while data in packet to read
                    while ((charsRead = receive.read(chars)) != -1) {
                        // write to file
                        // exclude trailing null chars
                        String stringToWrite = new String(chars, 0, charsRead).replace("\u0000", "");
                        bufferedWriter.write(stringToWrite);
                        dataReceived = true;
                    }
                    bufferedWriter.close();
                    // server has not sent data; delete file
                    if (!dataReceived) {
                        File file = new File(filename);
                        file.delete();
                        System.err.println("Nothing received - the requested file may not exist.");
                    } else {
                        System.out.println("File has been received.");
                    }
                } catch (IOException e) {
                    System.err.println("Filename error.");
                }
            }
            // write request
            else if (op_code.equals(OP_WRQ)) {
                // read chars from file
                try {
                    fileReader = new FileReader(filename);
                    System.out.println("Sending data...");
                    // write chars to sendPacket until end of file
                    int read;
                    while ((read = fileReader.read(chars)) != -1) {
                        String charString = new String(chars);
                        send.writeChars(charString);
                    }
                    fileReader.close();
                    System.out.println("File has been sent.");
                } catch (FileNotFoundException e) {
                    System.err.println("Filename not found");
                }
                if (bufferedWriter != null) {
                    bufferedWriter.close();
                }
                if (fileReader != null) {
                    fileReader.close();
                }
                clientSocket.close();
            }
        } catch (UnknownHostException e) {
            System.err.println("Problem with address " + address);
            System.exit(1);
        }  catch (IOException e) {
            System.err.println("Problem with establishing connection. Server may not be running");
            System.exit(1);
        }
    }
}