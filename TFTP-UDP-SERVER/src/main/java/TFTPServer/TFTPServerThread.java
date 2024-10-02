package TFTPServer;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * TFTPServerThread - this class is for supporting simultaneous file
 * transfers to and from multiple clients by subclassing the Thread class.
 * @author 246644
 * @version 2023
 */
public class TFTPServerThread extends Thread {
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

    private byte[] buffer;
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket serverSocket;
    private InetAddress clientAddress;
    private int clientPort;
    private int length;
    private String filename;

    public TFTPServerThread(DatagramPacket rp) throws SocketException {
        super("TFTPServerThread");
        System.out.println("Creating thread...");
        // create socket with any available port
        serverSocket = new DatagramSocket();
        // copy data from parameter DatagramPacket "rp" to local copy
        buffer = Arrays.copyOf(rp.getData(), rp.getData().length);
        length = rp.getLength();
        clientAddress = rp.getAddress();
        clientPort = rp.getPort();
        receivePacket = new DatagramPacket(buffer, PACKET);
        receivePacket.setPort(clientPort);
        receivePacket.setAddress(clientAddress);
        receivePacket.setLength(length);
        sendPacket = new DatagramPacket(buffer, PACKET);
        filename = null;
    }

    @Override
    /**
     * This method is called when a Thread object is started
     */
    public void run() {
        System.out.println("Thread running.");
        try {
            serverSocket.setSoTimeout(TIMEOUT);
        } catch (SocketException e) {
            System.err.println("UDP error.");
            e.printStackTrace();
        }

        // if opcode is RRQ or WRQ, filename should follow
        // extract filename and print message
        if (buffer[OFFSET_REQUEST] == OP_RRQ || buffer[OFFSET_REQUEST] == OP_WRQ) {
            filename = new String(buffer,2,receivePacket.getLength()-2);
            System.out.println("Filename requested: " + filename);
        }

        switch (buffer[OFFSET_REQUEST]) {
            case OP_RRQ: // read request - opcode: 01
                try {
                    // check if requested file exists, if not send error to client
                    File file = new File(filename);
                    if (file.exists()) {
                        readRequest();
                    } else {
                        sendError("File does not exist.", clientAddress, clientPort);
                    }
                } catch (IOException e) {
                    System.err.println("I/O error");
                    e.printStackTrace();
                }
                break;
            case OP_WRQ: // write request - opcode: 02
                try {
                    writeRequest();
                } catch (IOException e) {
                    System.err.println("I/O error");
                    e.printStackTrace();
                }
                break;
        }
    }

    /**
     * Process read request
     */
    public void readRequest() throws IOException {
        System.out.println("Read request...");

        // try to get requested file
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filename);
            // set socket timeout

            boolean endOfFile = false;
            int block = 1;
            // loop sending packets to the client until a short packet arrives
            // which indicates the end of the file
            do {
                // set buffer opcode to data
                buffer[0] = 0;
                buffer[OFFSET_REQUEST] = OP_DATA; // opcode for data = 03
                // set buffer block number
                buffer[2] = (byte) (block >> 8);
                buffer[3] = (byte) block;
                // read data into the buffer 512 bytes at a time
                int read = inputStream.read(buffer, 4, buffer.length-4);
                // if less than 512 bytes have been read, end of file reached
                if (read < 512) {
                    endOfFile = true;
                }
                System.out.println("Bytes read: " + read);
                System.out.println("Sending packet " + block + " to client...");
                sendPacket.setAddress(clientAddress);
                sendPacket.setPort(clientPort);
                sendPacket.setData(buffer);
                sendPacket.setLength(4+read); // opcode + block# + data = (2+2) + read
                serverSocket.send(sendPacket);

                // wait for ACK from client
                System.out.println("Waiting for ACK from client...");
                try {
                    serverSocket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    System.err.println("Socket timeout...");
                }

                // check if error code received
                if (buffer[OFFSET_REQUEST] == OP_ERROR) {
                    System.err.println("TftpServer error: " + new String(buffer, 2, buffer.length-2));
                    return;
                }
                // ACK received
                else if (buffer[OFFSET_REQUEST] == OP_ACK) {
                    System.out.println("ACK received");
                    // get block number
                    int ackBlock = ((buffer[2] & 0xff) << 8) | (buffer[3] & 0xff);
                    // client has acknowledged the last block, move onto next block
                    if (ackBlock == block) {
                        System.out.println("Correct block");
                        // max size of two bytes reached, start at zero again
                        if (block == 65535) {
                            block = 0;
                        }
                        else {
                            block++;
                        }
                    }
                    //else if {
                    // blocks do not match, resend previous block
                    // above results in Sorcerer's Apprentice Syndrome
                    //}
                }
            }  while (!endOfFile);

            inputStream.close();
            System.out.println("File sent.");

        } catch (FileNotFoundException e) {
            // send error to client
            System.err.println("Specified file not found.");
            sendError("SERVER ERROR: Cannot find specified filename", clientAddress, clientPort);
        }
    }

    /**
     * Process write request
     * For the client to be able to communicate with this new thread
     * it will need the address and port number.
     * Otherwise it will send the OP_WRQ to the server and then OP_DATA
     *
     */
    public void writeRequest() throws IOException {
        System.out.println("Write request...");

        // ensuring data from received packet is in buffer
        // error otherwise - length from first packet is saved
        buffer = Arrays.copyOf(receivePacket.getData(), receivePacket.getData().length);
        receivePacket.setData(buffer);

        // set socket timeout
        int block = 0;
        // ready to receive file
        System.out.println("Ready to receive packets from client.");
        // no data received, send ACK to client
        if (block == 0) {
            // send ACK
            buffer[0] = 0;
            buffer[OFFSET_REQUEST] = OP_ACK;
            buffer[2] = buffer[3] = 0;
            sendPacket.setData(buffer);
            sendPacket.setLength(4);
            sendPacket.setAddress(clientAddress);
            sendPacket.setPort(clientPort);
            serverSocket.send(sendPacket);
            System.out.println("ACK sent to client: " + clientAddress + ", " + clientPort);
        }
        BufferedWriter bufferedWriter = null;
        try {
            // Create file with given name
            bufferedWriter = new BufferedWriter(new FileWriter(filename));

            block = 1; // starting to receive first block
            boolean dataReceived = false;
            // loop to receive packets until small one received
            // write to file and send ACK back to client
            do {
                // receive packet from client
                try {
                    serverSocket.receive(receivePacket);
                } catch (SocketTimeoutException e) {
                    System.err.println("Socket timed out");
                }
                // check if error code received
                if (buffer[OFFSET_REQUEST] == OP_ERROR) {
                    System.err.println("\nError received from client.");
                }
                // data received from client
                else if (buffer[OFFSET_REQUEST] == OP_DATA) {
                    // get block number
                    System.out.println("Data packet received: " + (receivePacket.getLength()-4));
                    int dataBlock = ((buffer[2] & 0xff) << 8) | (buffer[3] & 0xff);
                    // Correct data, write to file and send ACK
                    // received block number in buffer is same as expected
                    if (dataBlock == block) {
                        System.out.println("Correct block");
                        // write data from the buffer to file
                        String bufferData = new String(buffer, 4, receivePacket.getLength()-4);
                        bufferedWriter.write(bufferData);
                        dataReceived = true;
                        // send ACK packet to client
                        // data block in buffer is already correct, no need to change
                        System.out.println("Sending ACK to client");
                        buffer[0] = 0;
                        buffer[OFFSET_REQUEST] = OP_ACK;
                        sendPacket.setLength(4);
                        sendPacket.setPort(receivePacket.getPort());
                        sendPacket.setAddress(receivePacket.getAddress());
                        serverSocket.send(sendPacket);
                        System.out.println("ACK sent to client" + receivePacket.getAddress() + ", " + receivePacket.getPort());
                        // increment block, to move to next one
                        block++;
                    }
                    // data received is not correct, send error
                    else {
                        // set buffer for error opcode
                        buffer[0] = 0;
                        buffer[OFFSET_REQUEST] = OP_ERROR;
                        sendPacket.setAddress(receivePacket.getAddress());
                        sendPacket.setPort(receivePacket.getPort());
                        sendPacket.setData(buffer);
                        sendPacket.setLength(4);
                        // send error to client
                        serverSocket.send(sendPacket);
                    }
                }
                // loop until packet is smaller than packet size; end of data
            } while (receivePacket.getLength() == PACKET);

            // close bufferWriter
            bufferedWriter.close();
            // check if data was received
            // empty file may have been created; delete it
            if (!dataReceived) {
                File file = new File(filename);
                file.delete();
                System.out.println("No data received.");
            } else {
                System.out.println("File received.");
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * Send error to client, pass detail to error() as parameter
     * @param clientAddress
     * @param clientPort
     */
    public void sendError(String errorMessage, InetAddress clientAddress, int clientPort) throws IOException {
        System.out.println("Sending error message...");
        byte[] error = errorMessage.getBytes();
        buffer[0] = 0;
        buffer[OFFSET_REQUEST] = OP_ERROR; // error opcode: 05
        int length = 2;
        for (int i = 0; i < error.length; i++) {
            buffer[i+2] = error[i];
            length++;
        }
        sendPacket.setData(buffer);
        sendPacket.setAddress(clientAddress);
        sendPacket.setPort(clientPort);
        sendPacket.setLength(length);
        serverSocket.send(sendPacket);
        System.out.println("Error sent.");
    }
}
