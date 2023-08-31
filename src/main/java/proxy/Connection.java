package proxy;

import org.xbill.DNS.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class Connection {
    private State state;
    private Selector selector;
    private DatagramChannel dnsServer;
    private InetSocketAddress dnsServerAddr = ResolverConfig.getCurrentConfig().servers().get(0);
    private SocketChannel client;
    private SocketChannel server;
    private SelectionKey clientKey;
    private SelectionKey serverKey;
    private ByteBuffer readBufClient = ByteBuffer.allocate(2048);
    private ByteBuffer writeBufClient = null;

    private ByteBuffer readBufServer = ByteBuffer.allocate(2048);
    private ByteBuffer writeBufServer = null;


    public Connection(Selector selector, SocketChannel client, DatagramChannel dnsServer) throws IOException{
        state = State.GREETING;
        this.selector = selector;
        this.dnsServer = dnsServer;
        this.client = client;
        client.configureBlocking(false);
        clientKey = client.register(selector, SelectionKey.OP_READ, this);
    }

    public void handle(SelectionKey key) throws IOException{
        SelectableChannel socketChannel = key.channel();
        if(socketChannel == client){
            if (key.isReadable() && state == State.GREETING){
                sendGreeting();
            }
            else if(key.isReadable() && state == State.CONNECTING){
                connectToServer();
            }
            else if (key.isReadable() && state == State.CONNECTED){
                readFromClient();
            }
            else if(key.isWritable()){
                writeOnClient();
            }
        }
        else {
            if (key.isReadable()){
                readFromServer();
            }
            else if(key.isWritable()){
                writeOnServer();
            }
        }
    }

    private void sendGreeting() throws IOException {
        try {
            client.read(readBufClient);
            byte[] bytes = readBufClient.array();
            byte[] answer = new byte[2];
            answer[0] = 5;
            if (bytes[0] != 5) {
                answer[1] = (byte) 0xff;
            } else {
                answer[1] = (byte) 0x00;
            }
            clientKey.interestOps(SelectionKey.OP_WRITE);
            //clientKey = client.register(selector, SelectionKey.OP_WRITE, this);
            writeBufClient = ByteBuffer.wrap(answer);
        } catch (IOException e){
            closeClient();
        }
    }

    private void connectToServer() throws IOException{
        ByteBuffer buf = ByteBuffer.allocate(256);
        try {
            int count = client.read(buf);
            byte[] bytes = buf.array();
            byte[] answer = new byte[count];
            System.arraycopy(bytes, 0, answer, 0, count);
            if (bytes[0] != 5 || bytes[1] != 1) {
                answer[1] = (byte) 0x07;
            }
            else if(bytes[3] == 1) {
                byte[] ip = new byte[4];
                System.arraycopy(bytes, 4, ip, 0, 4);
                int port = ((bytes[8] & 0xff) << 8) | (bytes[9] & 0xff);
                try {
                    server = SocketChannel.open(new InetSocketAddress(InetAddress.getByAddress(ip), port));
                    server.configureBlocking(false);
                    System.out.println("Connect to " + InetAddress.getByAddress(ip) + " " + port);
                    answer[1] = (byte) 0x00;
                } catch (IOException e) {
                    answer[1] = (byte) 0x01;
                }
                writeBufClient = ByteBuffer.wrap(answer);
                clientKey.interestOps(SelectionKey.OP_WRITE);
            }
            else if(bytes[3] == 3) {
                int length = bytes[4];
                byte[] name = new byte[length];
                System.arraycopy(bytes, 5, name, 0, length);
                InetAddress address = Address.getByName(new String(name));
                byte[] ip = address.getAddress();
                int port = ((bytes[5+length] & 0xff) << 8) | (bytes[6+length] & 0xff);
                try {
                    server = SocketChannel.open(new InetSocketAddress(InetAddress.getByAddress(ip), port));
                    server.configureBlocking(false);
                    System.out.println("Connect to " + new String(name) + " " + port);
                    answer[1] = (byte) 0x00;
                } catch (IOException e) {
                    answer[1] = (byte) 0x01;
                }
                writeBufClient = ByteBuffer.wrap(answer);
                clientKey.interestOps(SelectionKey.OP_WRITE);
            }
            else {
                answer[1] = (byte) 0x07;
            }
        } catch (IOException e){
            closeClient();
        }
    }

    private void readFromServer() throws IOException{
        readBufServer.clear();
        int res = -1;
        try {
            res = server.read(readBufServer);
        } catch (IOException e){
            closeServer();
        }
        if(res == -1){
            closeServer();
        }
        else if(res > 0){
            byte[] write = new byte[res];
            System.arraycopy(readBufServer.array(), 0, write, 0, res);
            writeBufClient = ByteBuffer.wrap(write);
            if(clientKey.isValid()) {
                clientKey.interestOps(SelectionKey.OP_WRITE);
            }
            serverKey.interestOps(0);
        }
    }

    private void writeOnServer() throws IOException{
        try {
            server.write(writeBufServer);
            serverKey.interestOps(SelectionKey.OP_READ);
        } catch(IOException e){
            closeServer();
        }
    }

    private void readFromClient() throws IOException{
        readBufClient.clear();
        try {
            int res = client.read(readBufClient);
            if (res == -1) {
                closeClient();
            } else if (res > 0) {
                byte[] write = new byte[res];
                System.arraycopy(readBufClient.array(), 0, write, 0, res);
                writeBufServer = ByteBuffer.wrap(write);
                serverKey = server.register(selector, SelectionKey.OP_WRITE, this);
            }
        } catch (IOException e){
            closeClient();
        }
    }

    private void writeOnClient() throws IOException{
        client.write(writeBufClient);
        if(state == State.GREETING){
            state = State.CONNECTING;
            clientKey.interestOps(SelectionKey.OP_READ);
        }
        else if (state == State.CONNECTING){
            state = State.CONNECTED;
            clientKey.interestOps(SelectionKey.OP_READ);
        }
        else{
            clientKey = client.register(selector, SelectionKey.OP_READ, this);
            serverKey = server.register(selector, SelectionKey.OP_READ, this);
        }
    }

    private void closeClient() throws IOException{
        if(client != null) {
            client.close();
        }
        clientKey.cancel();
    }
    private void closeServer() throws IOException{
        if(server != null) {
            server.close();
        }
        serverKey.cancel();
    }
}

enum State{
    GREETING,
    CONNECTING,
    CONNECTED

}
