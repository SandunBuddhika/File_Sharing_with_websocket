package com.sandun.app.webSocket;

import java.io.*;
import java.util.Scanner;

public class Client4 {
    private static Scanner scanner = new Scanner(System.in);
    private static File file = new File("src/main/resources/abc.txt");

    public static void main(String[] args) throws IOException {
        WebSocketManager wsm = new WebSocketManager().init("2", "Namindu");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                wsm.connectToAHost("66d593039b39f");
                Thread.sleep(3000);
                reader(wsm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void reader(WebSocketManager wsm) {
        String input = scanner.next();
        if (input.equals("1")) {
            wsm.startSharing(file);
        } else if (input.equals("2")) {
            wsm.sharing(file);
        }
        reader(wsm);
    }
}
