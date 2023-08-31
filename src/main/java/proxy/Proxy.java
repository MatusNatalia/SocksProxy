package proxy;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

public class Proxy {
    private Selector selector;
    private ServerSocketChannel serverSocket;
    private DatagramChannel dnsServer;

    public Proxy(int port) {
        try {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
            serverSocket.configureBlocking(false);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port));
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            dnsServer = DatagramChannel.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws IOException {
        Connection connection;
        while (true) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    startConnection();
                } else {
                    connection = (Connection) key.attachment();
                    connection.handle(key);
                }
            }
        }
    }

    private void startConnection() throws IOException {
        SocketChannel client = serverSocket.accept();
        new Connection(selector, client, dnsServer);
    }
}
