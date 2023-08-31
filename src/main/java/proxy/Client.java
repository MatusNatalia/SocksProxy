package proxy;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class Client {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        DatagramSocket socket = new DatagramSocket();
        Record queryRecord = Record.newRecord(Name.fromString("fit.ippolitov.me."), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);
        socket.send(new DatagramPacket(queryMessage.toWire(), queryMessage.numBytes(), ResolverConfig.getCurrentConfig().servers().get(0)));
        byte[] answer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(answer, 1024, ResolverConfig.getCurrentConfig().servers().get(0));
        socket.receive(packet);
        Message message = new Message(packet.getData());
        for(Record record : message.getSection(Section.ANSWER)){
            if(record instanceof ARecord){
                InetAddress address = ((ARecord) record).getAddress();
                System.out.println(address);
            }
        }
        System.out.println(Address.getByName("fit.ippolitov.me"));
        //System.out.println(message);
        //ARecord record = (ARecord)message.getSection(Section.ANSWER).get(0);
        //InetAddress address = record.getAddress();
        //System.out.println(address.getAddress());
    }
}
