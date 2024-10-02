package TftpTCPServer;

import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {

    private static final String OP_RRQ = "01";
    private static final String OP_WRQ = "02";
    private Socket slaveSocket;
    private String op_code;
    private String filename;
    private String userRequest;
    // write to socket using send and receive objects below
    private DataOutputStream send;
    private BufferedReader receive;
    // to write/read to/from files use FileReader and BufferedWriter
    BufferedWriter bufferedWriter;
    FileReader fileReader;

    public ClientHandler(Socket socket) {
        super("ClientHandler");
        this.slaveSocket = socket;
        bufferedWriter = null;
        fileReader = null;
        op_code = filename = userRequest = null;
    }

    @Override
    /**
     * Overriding run() from Thread class
     * This method gets requests from clients and responds to them
     */
    public void run() {
        try {
            slaveSocket.setSoTimeout(5000);
            send = new DataOutputStream(slaveSocket.getOutputStream());
            receive = new BufferedReader(new InputStreamReader(slaveSocket.getInputStream()));

            // get request from client
            userRequest = receive.readLine().trim();
            op_code = userRequest.substring(0, 2);
            filename = userRequest.substring(2);
            // check if opcode is valid
//            if (!op_code.equals(OP_RRQ) || !op_code.equals(OP_WRQ))
//            {
//                System.err.println("Incorrect opcode");
//                slaveSocket.close();
//            }

            // process requests
            char[] chars = new char[1024];

            // read request
            // get data from file and send to client
            // check if a filename has been given
            if (op_code.equals(OP_RRQ) && !filename.isEmpty()) {
                System.out.println("Processing read request...");
                try {
                    fileReader = new FileReader(filename);
                    System.out.println("Sending data...");
                    // write chars to sendPacket until end of file
                    int read;
                    while ((read = fileReader.read(chars)) != -1) {
                        String charString = new String(chars);
                        send.writeChars(charString);
                    }
                    System.out.println("File has been sent.");
                    fileReader.close();
                } catch (FileNotFoundException e) {
                    System.err.println("Filename not found");
                }
            }
            // write request
            // get data from client and write to file
            // check if a filename has been given
            else if (op_code.equals(OP_WRQ) && !filename.isEmpty()) {
                System.out.println("Processing write request...");
                try {
                    bufferedWriter = new BufferedWriter(new BufferedWriter(new FileWriter(filename)));
                    System.out.println("Awaiting data from client...");
                    int charsRead;
                    // to ensure client has sent data, creating boolean flag to check
                    boolean dataReceived = false;
                    // read chars from received packet until end
                    while ((charsRead = receive.read(chars)) != -1) {
                        // write to file
                        // excluding any trailing null characters
                        String stringToWrite = new String(chars, 0, charsRead).replace("\u0000", "");
                        bufferedWriter.write(stringToWrite);
                        dataReceived = true;
                    }
                    bufferedWriter.close();
                    // client has not sent data; delete the file
                    if (!dataReceived) {
                        File file = new File(filename);
                        file.delete();
                        System.err.println("No data received.");
                    } else {
                        System.out.println("File received.");
                    }
                } catch (SocketTimeoutException e) {
                    System.err.println("Socket timeout");
                    e.printStackTrace();
                } catch (IOException e) {
                    System.err.println("Filename error.");
                    e.printStackTrace();
                }
            }
            else if (filename.isEmpty()) {
                System.err.println("No filename given.");
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (fileReader != null) {
                fileReader.close();
            }
            slaveSocket.close();

        }
        catch (IOException e) {
            System.err.println("Error: Client terminated or sent an invalid request");
        }
    }
}