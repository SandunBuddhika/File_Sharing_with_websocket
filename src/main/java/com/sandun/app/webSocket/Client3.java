package com.sandun.app.webSocket;

import java.util.Scanner;

public class Client3 {
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        WebSocketManager wsm = new WebSocketManager().init("1", "Sandun");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                wsm.createAHostSession();
                reader(wsm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void reader(WebSocketManager wsm) {
        String input = scanner.next();
        if (input.equals("1")) {
            wsm.isReceivingFIle(true);
        }
        reader(wsm);
    }
}

