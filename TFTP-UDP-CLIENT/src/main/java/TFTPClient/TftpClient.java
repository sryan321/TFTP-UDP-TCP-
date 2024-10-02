package TFTPClient;

import java.io.*;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.net.*;

/**
 * TFTP Client - sends read and write requests (RRQ/WRQ) to a server
 * NOTE - only supports octet mode
 * @author 246644
 * @version 2023
 */
public class TftpClient {
    // buffer[1] is the significant byte for the opcode, so offset = 1
    public final int OFFSET_REQUEST = 1;
    // op-code for Read Request
    public final int OP_RRQ = 1;
    // op-code for Write Request
    public final int OP_WRQ = 2;
    // op-code for Data Request
    public final int OP_DATA = 3;
    // op-code for Acknowledgement
    public final int OP_ACK = 4;
    // op-code for Error
    public final int OP_ERROR = 5;
    // Set datagram socket timeout = 5 seconds = 5,000 ms
    public final static int TIMEOUT = 5000;
    // packet size = opcode (2 bytes) + block number (2 bytes) + data (512 bytes)
    protected final static int PACKET = 516;
    protected InetAddress serverAddress;
    protected String server;
    private int serverPort;
    // create Datagram Socket to listen for incoming datagrams
    protected DatagramSocket clientSocket;
    protected DatagramPacket receivePacket, sendPacket;
    protected byte buffer[];
    //declare fields
    private Scanner scanner;
    private String filename;
    private int instruction;

    /**
     * Main program that runs the network client
     *
     * @param args[0] address of server
     * @param args[1] port number of server
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // Check that both required input arguments are passed.
        if (args.length != 2) {
            System.err.println("Usage: java TftpClient <address> <port>");
            System.exit(1);
        }
        TftpClient client = new TftpClient(args[0], args[1]);
        client.run();
    }

    /**
     * Create new TFTP client
     * @param address - address of the server
     * @param port - port number of the server
     */
    public TftpClient(String address, String port) throws IOException {
        System.out.println("Creating client...");
        serverAddress = InetAddress.getByName(address);
        serverPort = Integer.parseInt(port);
        clientSocket = new DatagramSocket(2345);
        // set timeout for socket to 5 seconds
        clientSocket.setSoTimeout(TIMEOUT);
        buffer = new byte[PACKET];
        receivePacket = new DatagramPacket(buffer, PACKET);
        sendPacket = new DatagramPacket(buffer, PACKET, serverAddress, serverPort);
    }

    /**
     * Run client
     * Simple console based system to get request and filename from client
     */
    public void run() throws IOException {
        System.out.println("Client running...");

        // get user instructions
        scanner = new Scanner(System.in);
        System.out.println("Enter '1' to read a file or '2' to write a file: ");
        try {
            instruction = scanner.nextInt();
        } catch (InputMismatchException e) {
            System.err.println("Incorrect input");
            System.exit(1);
        }
        scanner.nextLine(); // consume newline character

        System.out.println("Enter the file name to retrieve the server: ");
        filename = scanner.nextLine();
        switch (instruction) {
            case 1: // read file
                readRequest(filename);
                break;
            case 2: // write file
                // check if file exist before requesting to write it to the server
                File file = new File(filename);
                if (file.exists()) {
                    writeRequest(filename);
                } else {
                    System.out.println("File does not exist.");
                }
                break;
            default:
                System.out.println("Invalid opcode - only enter '1' or '2'.");
        }
    }

    /**
     * Build tftp read request packet in buffer to send to server
     * Get packets from server until a smaller one arrives, indicating end of file
     * Send ACK back to server upon receipt of packets
     *
     * @param filename
     */
    public void readRequest(String filename) throws IOException {
        BufferedWriter bufferedWriter = null;
        try {
            bufferedWriter = new BufferedWriter(new FileWriter(filename));

            System.out.println("Request file: " + filename);
            // set opcode to read request
            buffer[0] = 0; // read request - opcode: 01
            buffer[OFFSET_REQUEST] = OP_RRQ;
            // length of data in buffer
            int length = 2;
            // convert filename string to bytes in buffer
            byte[] filenameBytes = filename.getBytes();
            // add bytes to buffer
            for (int i = 0; i < filenameBytes.length; i++) {
                buffer[i + 2] = filenameBytes[i];
                length++;
            }
            // send RRW to server
            sendPacket.setLength(length);
            clientSocket.send(sendPacket);

            //  loop reading packets received from server until small one arrives
            // write packets to file and send ACK back to server
            int block = 1;
            // creating flag to check if data has been received
            boolean dataReceived = false;
            do {
                // receive packet from server
                try {
                    clientSocket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    System.err.println("Socket timed out - the server may not be reachable.");
                    System.exit(1);
                }
                // check if error code received
                if (buffer[OFFSET_REQUEST] == OP_ERROR) {
                    System.out.write(buffer, 2, receivePacket.getLength() - 2);
                    System.exit(1);
                }
                // data received from server
                else if (buffer[OFFSET_REQUEST] == OP_DATA) {
                    System.out.println("Data packet received: " + (receivePacket.getLength()-4));
                    int dataBlock = ((buffer[2] & 0xff) << 8 | buffer[3] & 0xff);
                    // Correct data, write to file and send ACK
                    // received block number in buffer is same as expected
                    if (dataBlock == block) {
                        System.out.println("Correct block");
                        // write data from buffer to file
                        String bufferData = new String(buffer, 4, receivePacket.getLength() - 4);
                        bufferedWriter.write(bufferData);
                        dataReceived = true;
                        // send ACK packet to server
                        // data block number in buffer is already correct, no need to change
                        buffer[0] = 0;
                        buffer[OFFSET_REQUEST] = OP_ACK;
                        sendPacket.setLength(4);
                        sendPacket.setPort(receivePacket.getPort());
                        sendPacket.setAddress(receivePacket.getAddress());
                        clientSocket.send(sendPacket);
                        block++;
                    }
                    // incorrect data received, send error
                    else {
                        buffer[0] = 0;
                        buffer[OFFSET_REQUEST] = OP_ERROR;
                        clientSocket.send(sendPacket);
                    }
                }

            } while (receivePacket.getLength() == PACKET);
            // close bufferWriter
            bufferedWriter.close();
            // check if data was received
            // empty file may have been created; delete it
            if (!dataReceived) {
                File file = new File(filename);
                file.delete();
                System.err.println("No data received - file was empty or may not exist on the server");
            } else {
                System.out.println("File received.");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Process write request opcode
     * Build tftp write request packet in buffer to send to server
     * Send packets from file and wait for ACKs from the server until end of file
     *
     * @param filename
     */
    public void writeRequest(String filename) throws IOException {
        // build write request
        buffer[0] = 0;
        buffer[OFFSET_REQUEST] = OP_WRQ;
        // length of data in buffer
        int length = 2; // already contains header: opcode = 2
        // convert string filename to bytes and add to buffer
        byte[] filenameBytes = filename.getBytes();
        for (int i = 0; i < filenameBytes.length; i++) {
            buffer[i + 2] = filenameBytes[i];
            length++;
        }
        // send write request to server
        sendPacket.setData(buffer);
        sendPacket.setAddress(serverAddress);
        sendPacket.setPort(serverPort);
        sendPacket.setLength(length);
        clientSocket.send(sendPacket);
        System.out.println("Sent WRQ to server: " + sendPacket.getAddress() + ", " + sendPacket.getPort());

        // wait for ACK from server; when received begin sending data
        try {
            clientSocket.receive(receivePacket);
        } catch (SocketTimeoutException e) {
            System.err.println("Socket has timed out...");
        }
        // ACK received, begin sending data to server
        if (buffer[OFFSET_REQUEST] == OP_ACK) {
            System.out.println("Received ACK from server - beginning to send data...");
            FileInputStream inputStream = null;
            // try to get file
            try {
                inputStream = new FileInputStream(filename);

                boolean endOfFile = false;
                int block = 1;
                // loop sending packets to server until a shorter packet is built
                // this indicates the end of file and raises endOfFile flag
                do {
                    // set buffer opcode to data
                    buffer[0] = 0;
                    buffer[OFFSET_REQUEST] = OP_DATA; // opcode for data = 03
                    // set buffer block number
                    buffer[2] = (byte) (block >> 8);
                    buffer[3] = (byte) block;
                    // read data into the buffer 512 bytes at a time
                    int read = inputStream.read(buffer, 4, buffer.length - 4);
                    System.out.println("Bytes read: " + read);

                    // if less than 512 bytes read, end of file has been reached
                    if (read < 512) {
                        endOfFile = true;
                    }
                    // send packet to server
                    sendPacket.setAddress(receivePacket.getAddress());
                    sendPacket.setPort(receivePacket.getPort());
                    sendPacket.setData(buffer);
                    sendPacket.setLength(4 + read); // header (opcode=2 + blockNo=2) + data (bytes read)
                    System.out.println("Sending packet " + block + " to server...");
                    clientSocket.send(sendPacket);
                    System.out.println(sendPacket.getAddress() + ", " + sendPacket.getPort() + ": " + (sendPacket.getLength() - 4) + " bytes.");
                    // wait for ACK from server
                    System.out.println("Waiting for ACK from server...");
                    try {
                        clientSocket.receive(receivePacket);
                    } catch (SocketTimeoutException e) {
                        System.err.println("Socket has timed out...");
                    }
                    // check what opcode has been sent
                    if (buffer[OFFSET_REQUEST] == OP_ERROR) {
                        System.err.println(new String(buffer, 2, sendPacket.getLength() - 2));
                        System.exit(1);
                    }
                    // ACK received, can move on to next block
                    else if (buffer[OFFSET_REQUEST] == OP_ACK) {
                        System.out.println("ACK received");
                        // get block number
                        int ackBlock = ((buffer[2] & 0xff) << 8) | (buffer[3] & 0xff);
                        // server has acknowledged the last block, move to next block
                        if (ackBlock == block) {
                            // if block == 65535, max size reached; start a 0 again
                            if (block == 65535) {
                                block = 0;
                            } else {
                                block++;
                            }
                        }
                    }
                } while (!endOfFile);

                inputStream.close();
                System.out.println("File sent to server.");
                System.exit(0);
            } catch (FileNotFoundException e) {
                System.err.println("File not found");
                System.exit(1);
            }
        }
        // No ACK received from server
        else {
            System.err.write(buffer, 2, receivePacket.getLength() - 2);
            System.exit(1);
        }
    }
}