package proxy;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Proxy proxy = new Proxy(8889);
        try {
            proxy.run();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}